package alick.diginex.orderbook;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.CancelRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.request.Request;
import alick.diginex.orderbook.response.*;
import alick.diginex.orderbook.response.Level2Summary.PriceQuantity;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.DoubleArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;

import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

/**
 * The order book, has bid/ask queue at different price. The order book tracks the minimal amount of information:
 * <ul>
 *     <li>order id</li>
 *     <li>quantity remaining for each of the order</li>
 * </ul>
 * <p>
 * There is only one point of entry into the order book, {@link #submitRequest(Request)}, which supports new / amend / cancel of orders.
 * <p>
 * Thread-safety: This order book is <em>not</em> thread-safe.
 */
public class OrderBook {
	private static final int DEFAULT_INITIAL_QUEUE_SIZE = 10;

	/**
	 * Tracks the list of prices for which there is a bucket list for buy orders
	 * <p/>
	 * The index order is maintained between this and {@link #buyBucketList}
	 */
	private final DoubleArrayList buyPriceList;
	/**
	 * order buckets for buy orders
	 */
	private final FastList<OrderBucket> buyBucketList;

	/**
	 * Tracks the list of prices for which there is a bucket list for sell orders
	 */
	private final DoubleArrayList sellPriceList;
	/**
	 * order buckets for sell orders
	 */
	private final FastList<OrderBucket> sellBucketList;

	// in case a market order has residual quantity after wiping out the other side
	// in NYSE the residual market order is not displayed
	// another option is to reject residual market order , like japan does?
	private final OrderBucket buyMarketOrderBucket;
	private final OrderBucket sellMarketOrderBucket;

	// this is used to look up which bucket an order falls into
	// eclipse collection LongObjectHashMap would have been much more efficient
	private final LongObjectHashMap<OrderBucket> orderId2OrderBucket = new LongObjectHashMap<>();

	/**
	 * The price at which the most recent trade was executed, or the reference price/IEP for an IPO stock.
	 * <p/>
	 * This is the price to trade at if we are crossing two market order (or should that be mid price ?)
	 */
	private double lastPrice;

	/**
	 * Create an instance of order book starting with default number of slots ({@value OrderBook#DEFAULT_INITIAL_QUEUE_SIZE}) for spreads
	 *
	 * @param referencePrice the price the instrument was last traded to use as initial reference
	 * @see OrderBook#OrderBook(double, int)
	 */
	public OrderBook(final double referencePrice) {
		this(referencePrice, DEFAULT_INITIAL_QUEUE_SIZE);
	}

	/**
	 * Create an instance of order book starting with the given number of slots for spreads.
	 *
	 * @param referencePrice the price the instrument was last traded to use as initial reference
	 * @param initialSpreads number of spreads to initialize with. For efficiency purposes.
	 */
	public OrderBook(final double referencePrice, final int initialSpreads) {
		this.lastPrice = referencePrice;
		this.buyBucketList = new FastList<>(initialSpreads);
		this.buyPriceList = new DoubleArrayList(initialSpreads);
		this.buyMarketOrderBucket = OrderBucket.builder().priceOfBucket(0.0).build();

		this.sellBucketList = new FastList<>(initialSpreads);
		this.sellPriceList = new DoubleArrayList(initialSpreads);
		this.sellMarketOrderBucket = OrderBucket.builder().priceOfBucket(0.0).build();
	}

	/**
	 * Submit a series of requests into the order book to be executed in the same traversal order.
	 *
	 * @param request request to be executed
	 * @return If any of the request results in a failure, then an {@link ErrorResponse} is returned with all preceding
	 * requests executed; otherwise a {@link SuccessResponse} with the final state of the order book after all requests
	 * are executed.
	 * @throws IllegalArgumentException      if <em>requests</em> is empty or null
	 * @throws UnsupportedOperationException if the type of request is not recognized
	 */
	public Response submitRequest(final Request request) {
		Objects.requireNonNull(request, "request cannot be null");
		if (!(request instanceof NewRequest) && !(request instanceof CancelRequest) && !(request instanceof AmendRequest))
			throw new UnsupportedOperationException("The given request type : " + request.getClass().getSimpleName() + " for order ID " + request.getOrderId() + " is not supported");

		final Response curResponse;
		if (request instanceof NewRequest)
			curResponse = handleNewRequest((NewRequest) request);
		else if (request instanceof CancelRequest)
			curResponse = handleCancelRequest((CancelRequest) request);
		else
			curResponse = handleAmendRequest((AmendRequest) request);

		if (curResponse instanceof SuccessResponse) {
			return SuccessResponse.builder()
					.orderId(curResponse.getOrderId())
					.bidSummary(snapBucketList(this.buyBucketList))
					.askSummary(snapBucketList(this.sellBucketList))
					.executions(curResponse.getExecutions())
					.build();
		}
		else {
			return ErrorResponse.builder()
					.orderId(curResponse.getOrderId())
					.bidSummary(snapBucketList(this.buyBucketList))
					.askSummary(snapBucketList(this.sellBucketList))
					.errorMsg(((ErrorResponse) curResponse).getErrorMsg())
					.executions(curResponse.getExecutions())
					.build();
		}
	}

	private Response handleNewRequest(final NewRequest request) {
		final long orderId = request.getOrderId();
		final double orderQty = request.getQuantity();

		final OrderEntry orderEntry = new OrderEntry(orderId, orderQty);
		if (Side.BUY == request.getSide()) {
			return handleNewBuyRequest(request, orderEntry);
		}
		else {
			return handleNewSellRequest(request, orderEntry);
		}
	}

	@FunctionalInterface
	private interface PriceCompareFunction {
		boolean isBetterPrice(double price, double referencePrice);
	}

	private static final PriceCompareFunction BETTER_BUY_PRICE = (price, referencePrice) -> price > referencePrice;
	private static final PriceCompareFunction BETTER_SELL_PRICE = (price, referencePrice) -> price < referencePrice;

	private static OrderBucket retrieveBucketForPrice(
			final double orderPrice,
			final PriceCompareFunction priceCompareFunction,
			final DoubleArrayList priceList,
			final FastList<OrderBucket> bucketList) {
		final OrderBucket bucketForPrice;
		int bucketIndex = priceList.indexOf(orderPrice);
		if (bucketIndex >= 0) {
			bucketForPrice = bucketList.get(bucketIndex);
		}
		else { // the price does not exist. find the right place to put it
			int indexToInsert = -1;
			for (int i = 0; i < priceList.size(); ++i) {
				if (priceCompareFunction.isBetterPrice(orderPrice, priceList.get(i))) {
					indexToInsert = i;
					break;
				}
			}
			bucketForPrice = OrderBucket.builder().priceOfBucket(orderPrice).build();
			if (indexToInsert >= 0) {
				priceList.addAtIndex(indexToInsert, orderPrice);
				bucketList.add(indexToInsert, bucketForPrice);
			}
			else {
				priceList.add(orderPrice);
				bucketList.add(bucketForPrice);
			}
		}
		return bucketForPrice;
	}

	private Response handleNewBuyRequest(final NewRequest request, final OrderEntry initialOrderEntry) {
		final boolean isMarketOrder = OrderType.isMarketOrder(request.getOrderType());
		final ArrayList<Execution> executions = new ArrayList<>();

		// cross with any market order from the other side
		if (!this.sellMarketOrderBucket.isEmpty()) {
			final double marketOrderExecPrice = isMarketOrder ? lastPrice : request.getPrice();
			executions.addAll(matchOrderAgainstBucket(initialOrderEntry, Side.BUY, sellMarketOrderBucket, marketOrderExecPrice));
		}
		// if we still have quantity left to execute, loop over the limit orders and try to cross
		if (!initialOrderEntry.isOrderDone()) {
			if (isMarketOrder || (!sellPriceList.isEmpty() && request.getPrice() >= sellPriceList.get(0))) {
				for (int i = 0; i < sellPriceList.size() && !initialOrderEntry.isOrderDone(); ++i) {
					final double priceForExec = sellPriceList.get(i);
					if ((!isMarketOrder) && priceForExec > request.getPrice())
						break;
					final OrderBucket orderBucket = sellBucketList.get(i);
					if (orderBucket.isEmpty())
						continue;
					executions.addAll(matchOrderAgainstBucket(initialOrderEntry, Side.BUY, orderBucket, priceForExec));
				}
			}
		}

		if (!executions.isEmpty()) {
			final Execution x = executions.get(executions.size() - 1);
			lastPrice = x.getPrice();
		}

		// if still have quantity left, queue it
		if (!initialOrderEntry.isOrderDone()) {
			final OrderBucket bucketToEnqueue;
			if (isMarketOrder)
				bucketToEnqueue = buyMarketOrderBucket;
			else
				bucketToEnqueue = retrieveBucketForPrice(request.getPrice(), BETTER_BUY_PRICE, this.buyPriceList, this.buyBucketList);
			final long orderId = request.getOrderId();
			final boolean queueSuccess = queueOrderToBucket(orderId, initialOrderEntry, bucketToEnqueue, orderId2OrderBucket);
			if (!queueSuccess)
				return makeErrorResponse(orderId, "Unable to queue new order '" + orderId + "'");
		}

		return SuccessResponse.builder()
				.orderId(request.getOrderId())
				.bidSummary(snapBucketList(this.buyBucketList))
				.askSummary(snapBucketList(this.sellBucketList))
				.executions(executions)
				.build();
	}

	private Response handleNewSellRequest(final NewRequest request, final OrderEntry initialOrderEntry) {
		final boolean isMarketOrder = OrderType.isMarketOrder(request.getOrderType());
		final ArrayList<Execution> executions = new ArrayList<>();

		// cross with market order from the other side
		if (!this.buyMarketOrderBucket.isEmpty()) {
			final double marketOrderExecPrice = isMarketOrder ? lastPrice : request.getPrice();
			executions.addAll(matchOrderAgainstBucket(initialOrderEntry, Side.SELL, buyMarketOrderBucket, marketOrderExecPrice));
		}
		// if we still have quantity left to execute, loop over the limit orders and try to cross
		if (isMarketOrder || (!buyPriceList.isEmpty() && request.getPrice() <= buyPriceList.get(0))) {
			for (int i = 0; i < buyPriceList.size() && !initialOrderEntry.isOrderDone(); ++i) {
				final double priceForExec = buyPriceList.get(i);
				if ((!isMarketOrder) && priceForExec < request.getPrice())
					break;
				final OrderBucket orderBucket = buyBucketList.get(i);
				if (orderBucket.isEmpty())
					continue;
				executions.addAll(matchOrderAgainstBucket(initialOrderEntry, Side.SELL, orderBucket, priceForExec));
			}
		}

		if (!executions.isEmpty()) {
			final Execution x = executions.get(executions.size() - 1);
			lastPrice = x.getPrice();
		}

		// if still have quantity left, queue it
		if (!initialOrderEntry.isOrderDone()) {
			final OrderBucket bucketToEnqueue;
			if (isMarketOrder)
				bucketToEnqueue = sellMarketOrderBucket;
			else
				bucketToEnqueue = retrieveBucketForPrice(request.getPrice(), BETTER_SELL_PRICE, this.sellPriceList, this.sellBucketList);
			final long orderId = request.getOrderId();
			final boolean queueSuccess = queueOrderToBucket(orderId, initialOrderEntry, bucketToEnqueue, orderId2OrderBucket);
			if (!queueSuccess)
				return makeErrorResponse(orderId, "Unable to queue new order '" + orderId + "'");
		}

		return SuccessResponse.builder()
				.orderId(request.getOrderId())
				.bidSummary(snapBucketList(this.buyBucketList))
				.askSummary(snapBucketList(this.sellBucketList))
				.executions(executions)
				.build();
	}

	/**
	 * Tries to cross the given order entry with order in the incoming order bucket, using the given execution price
	 * for execution (in case the bucket is for  market order)
	 *
	 * @param fromOrderEntry the order entry to cross
	 * @param orderBucket    order bucket to cross against
	 * @param executionPrice price at which the execution is set to take price
	 * @return a list of execution which happened
	 */
	private static List<Execution> matchOrderAgainstBucket(final OrderEntry fromOrderEntry, final Side fromSide, final OrderBucket orderBucket, final double executionPrice) {
		final boolean isBuy = fromSide == Side.BUY;
		final MatchResult matchResult = orderBucket.matchOrder(fromOrderEntry);
		if (matchResult.getTotalMatchedQuantity() > 0) {
			fromOrderEntry.takeQuantity(matchResult.getTotalMatchedQuantity());
			return matchResult.getMatchedOrders().stream()
					.map(m -> Execution.builder()
							.buyOrderId(isBuy ? fromOrderEntry.getOrderId() : m.getOrderId())
							.sellOrderId(isBuy ? m.getOrderId() : fromOrderEntry.getOrderId())
							.quantity(m.getQuantity())
							.price(executionPrice)
							.build())
					.collect(toList());
		}
		return Collections.emptyList();
	}

	private static boolean queueOrderToBucket(final long orderId, final OrderEntry initialOrderEntry, final OrderBucket bucketToEnqueue, final LongObjectHashMap<OrderBucket> orderId2OrderBucket) {
		final boolean success = bucketToEnqueue.enqueueOrder(initialOrderEntry);
		if (success)
			orderId2OrderBucket.put(orderId, bucketToEnqueue);
		return success;
	}

	private Response handleCancelRequest(final CancelRequest request) {
		final long orderId = request.getOrderId();
		final OrderBucket bucket = this.orderId2OrderBucket.get(orderId);
		if (null == bucket) {
			return makeErrorResponse(orderId, "Order not found for ID '" + orderId + "'");
		}

		final boolean cancelled = bucket.cancelOrder(orderId);
		this.orderId2OrderBucket.remove(orderId);
		if (cancelled)
			return makeSuccessResponse(request.getOrderId());
		else
			return makeErrorResponse(orderId, "The given order ID '" + orderId + "' cannot be found");
	}

	private Response handleAmendRequest(final AmendRequest request) {
		final long orderId = request.getOrderId();
		final OrderBucket fromBucket = this.orderId2OrderBucket.get(orderId);
		if (null == fromBucket) {
			return makeErrorResponse(orderId, "The given order ID '" + orderId + "' cannot be found");
		}

		final OrderType newOrderType = request.getOrderType();
		final double newPrice = request.getNewPrice();
		final double newQty = request.getNewOrderQuantity();

		// original order was market order, check if we are changing order type as well
		if (fromBucket.equals(this.buyMarketOrderBucket) || fromBucket.equals(sellMarketOrderBucket)) {
			if (OrderType.MARKET == newOrderType) {
				final boolean success = fromBucket.resizeOrder(orderId, newQty);
				if (success)
					return makeSuccessResponse(request.getOrderId());
				else
					return makeErrorResponse(orderId, "The given order ID '" + orderId + "' cannot be found");
			}
			else { // mkt to limit, cancel + new
				return cancelNew(request, orderId, fromBucket);
			}
		}

		// if not amend price, then simply resize the order within bucket
		if (fromBucket.getPriceOfBucket() == newPrice) {
			final boolean resizeSuccess = fromBucket.resizeOrder(orderId, newQty);
			if (resizeSuccess)
				return makeSuccessResponse(request.getOrderId());
			else
				return makeErrorResponse(orderId, "The given order ID '" + orderId + "' cannot be found");
		}
		// we are changing price, then cancel + new
		return cancelNew(request, orderId, fromBucket);
	}

	private Response cancelNew(final AmendRequest request, final long orderId, final OrderBucket fromBucket) {
		final boolean cancelSuccess = fromBucket.cancelOrder(orderId);
		// TODO: is there a way to atomically remove and add to a different queue?
		if (!cancelSuccess)
			return makeErrorResponse(orderId, "Failed to amend the given order ID '" + orderId + "'");

		final NewRequest newRequest =
				NewRequest.builder()
						.orderId(request.getOrderId())
						.side(request.getSide())
						.orderType(request.getOrderType())
						.quantity(request.getNewOrderQuantity())
						.price(request.getNewPrice())
						.build();
		return handleNewRequest(newRequest);
	}

	private ErrorResponse makeErrorResponse(final long orderId, final String message) {
		return ErrorResponse.builder()
				.orderId(orderId)
				.bidSummary(snapBucketList(this.buyBucketList))
				.askSummary(snapBucketList(this.sellBucketList))
				.errorMsg(message)
				.executions(Collections.emptyList())
				.build();
	}

	private SuccessResponse makeSuccessResponse(final long orderId) {
		return SuccessResponse.builder()
				.orderId(orderId)
				.bidSummary(snapBucketList(this.buyBucketList))
				.askSummary(snapBucketList(this.sellBucketList))
				.executions(Collections.emptyList())
				.build();
	}

	private static final class LeadingEmptyOrderBucketFilter implements Predicate<OrderBucket> {
		boolean seenNonEmpty = false;

		@Override
		public boolean test(final OrderBucket orderBucket) {
			seenNonEmpty = seenNonEmpty || !orderBucket.isEmpty();
			return seenNonEmpty;
		}
	}

	private static Level2Summary snapBucketList(final List<OrderBucket> bucketList) {
		final Level2Summary.Builder l2Builder = Level2Summary.builder();
		bucketList.stream()
				.filter(new LeadingEmptyOrderBucketFilter())
				.map(b -> PriceQuantity.builder().price(b.getPriceOfBucket()).quantity(b.getQuantityInQueue()).build())
				.forEach(l2Builder::depth);

		return l2Builder.build();
	}

	public OrderBookSnapshot snapshotOrderBook() {
		final List<OrderBookSnapshot.OrderOpenQty> bidMarketQueue = this.buyMarketOrderBucket.getOrderEntryList().stream()
				.map(oe -> new OrderBookSnapshot.OrderOpenQty(oe.getOrderId(), oe.getRemainingQuantity()))
				.collect(toList());

		final List<OrderBookSnapshot.OrderOpenQty> askMarketQueue = this.buyMarketOrderBucket.getOrderEntryList().stream()
				.map(oe -> new OrderBookSnapshot.OrderOpenQty(oe.getOrderId(), oe.getRemainingQuantity()))
				.collect(toList());

		final LinkedHashMap<Double, List<OrderBookSnapshot.OrderOpenQty>> bidQueue = new LinkedHashMap<>();
		this.buyBucketList.stream()
				.filter(new LeadingEmptyOrderBucketFilter())
				.forEach(bucket -> {
					final List<OrderBookSnapshot.OrderOpenQty> depth = bucket.getOrderEntryList().stream()
							.map(oe -> new OrderBookSnapshot.OrderOpenQty(oe.getOrderId(), oe.getRemainingQuantity()))
							.collect(toList());
					bidQueue.put(bucket.getPriceOfBucket(), depth);
				});
		final LinkedHashMap<Double, List<OrderBookSnapshot.OrderOpenQty>> askQueue = new LinkedHashMap<>();
		this.sellBucketList.stream()
				.filter(new LeadingEmptyOrderBucketFilter())
				.forEach(bucket -> {
					final List<OrderBookSnapshot.OrderOpenQty> depth = bucket.getOrderEntryList().stream()
							.map(oe -> new OrderBookSnapshot.OrderOpenQty(oe.getOrderId(), oe.getRemainingQuantity()))
							.collect(toList());
					askQueue.put(bucket.getPriceOfBucket(), depth);
				});
		return new OrderBookSnapshot(bidMarketQueue, askMarketQueue, bidQueue, askQueue);
	}
}