package alick.diginex.orderbook;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.CancelRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.request.Request;
import alick.diginex.orderbook.response.*;

import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

/**
 * The order book, has bid/ask queue at different price. The order book tracks the minimal amount of information:
 * <ul>
 *     <li>order id</li>
 *     <li>quantity remaining for each of the order</li>
 * </ul>
 *
 * There is only one point of entry into the order book, {@link #submitRequest(Request)}, which supports new / amend / cancel of orders.
 *
 * Thread-safety: This order book is <em>not</em> thread-safe.
 */
public class OrderBook {
	private static final int DEFAULT_INITIAL_QUEUE_SIZE = 10;

	/**
	 * Tracks the list of prices for which there is a bucket list for buy orders
	 * <p/>
	 * The index order is maintained between this and {@link #buyBucketList}
	 */
	private final ArrayList<Double> buyPriceList;
	/**
	 * order buckets for buy orders
	 */
	private final ArrayList<OrderBucket> buyBucketList;

	/**
	 * Tracks the list of prices for which there is a bucket list for sell orders
	 */
	private final ArrayList<Double> sellPriceList;
	/**
	 * order buckets for sell orders
	 */
	private final ArrayList<OrderBucket> sellBucketList;

	// in case a market order has residual quantity after wiping out the other side
	// in NYSE the residual market order is not displayed
	// another option is to reject residual market order , like japan does?
	private final OrderBucket buyMarketOrderBucket;
	private final OrderBucket sellMarketOrderBucket;

	// this is used to look up which bucket an order falls into
	// eclipse collection LongObjectHashMap would have been much more efficient
	private final HashMap<Long, OrderBucket> orderId2OrderBucket = new HashMap<>();

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
		this.buyBucketList = new ArrayList<>(initialSpreads);
		this.buyPriceList = new ArrayList<>(initialSpreads);
		this.buyMarketOrderBucket = new OrderBucket(0.0);

		this.sellBucketList = new ArrayList<>(initialSpreads);
		this.sellPriceList = new ArrayList<>(initialSpreads);
		this.sellMarketOrderBucket = new OrderBucket(0.0);
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
		if ( !(request instanceof NewRequest) && !(request instanceof CancelRequest) && !(request instanceof AmendRequest))
			throw new UnsupportedOperationException("The given request type : " + request.getClass().getSimpleName() + " for order ID " + request.getOrderId() + " is not supported");

		final Response curResponse;
		if (request instanceof NewRequest)
			curResponse = handleNewRequest((NewRequest) request);
		else if (request instanceof CancelRequest)
			curResponse = handleCancelRequest((CancelRequest) request);
		else
			curResponse = handleAmendRequest((AmendRequest) request);

		if (curResponse instanceof SuccessResponse) {
			return new SuccessResponse(
					curResponse.getOrderId(),
					snapBucketList(this.buyBucketList),
					snapBucketList(this.sellBucketList),
					curResponse.getExecutions());
		}
		else {
			return new ErrorResponse(
					curResponse.getOrderId(),
					snapBucketList(this.buyBucketList),
					snapBucketList(this.sellBucketList),
					((ErrorResponse) curResponse).getErrorMsg(),
					curResponse.getExecutions());
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
			final ArrayList<Double> priceList,
			final ArrayList<OrderBucket> bucketList) {
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
			bucketForPrice = new OrderBucket(orderPrice);
			if (indexToInsert >= 0) {
				priceList.add(indexToInsert, orderPrice);
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

		return new SuccessResponse(
				request.getOrderId(),
				snapBucketList(this.buyBucketList),
				snapBucketList(this.sellBucketList),
				executions);
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

		return new SuccessResponse(
				request.getOrderId(),
				snapBucketList(this.buyBucketList),
				snapBucketList(this.sellBucketList),
				executions);
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
					.map(m -> new Execution(
							(isBuy ? fromOrderEntry.getOrderId() : m.getOrderId()),
							(isBuy ? m.getOrderId() : fromOrderEntry.getOrderId()),
							m.getQuantity(),
							executionPrice))
					.collect(toList());
		}
		return Collections.emptyList();
	}

	private static boolean queueOrderToBucket(final long orderId, final OrderEntry initialOrderEntry, final OrderBucket bucketToEnqueue, final HashMap<Long, OrderBucket> orderId2OrderBucket) {
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
				new NewRequest(
						request.getOrderId(),
						request.getSide(),
						request.getOrderType(),
						request.getNewOrderQuantity(),
						request.getNewPrice());
		return handleNewRequest(newRequest);
	}

	private ErrorResponse makeErrorResponse(final long orderId, final String message) {
		return new ErrorResponse(
				orderId,
				snapBucketList(this.buyBucketList),
				snapBucketList(this.sellBucketList),
				message,
				Collections.emptyList());
	}

	private SuccessResponse makeSuccessResponse(final long orderId) {
		return new SuccessResponse(
				orderId,
				snapBucketList(this.buyBucketList),
				snapBucketList(this.sellBucketList),
				Collections.emptyList());
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
		final List<Level2Summary.PriceQuantity> pxQtyList = bucketList.stream()
				.filter(new LeadingEmptyOrderBucketFilter())
				.map(b -> new Level2Summary.PriceQuantity(b.getPriceOfBucket(), b.getQuantityInQueue()))
				.collect(toList());
		return new Level2Summary(pxQtyList);
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