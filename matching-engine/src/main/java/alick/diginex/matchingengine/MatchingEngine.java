package alick.diginex.matchingengine;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.matchingengine.entities.ClientOrder;
import alick.diginex.matchingengine.entities.OrderStatus;
import alick.diginex.matchingengine.entities.Trade;
import alick.diginex.matchingengine.message.ExecutionReport;
import alick.diginex.matchingengine.message.OrderCancelReject;
import alick.diginex.matchingengine.message.ResponseMessage;
import alick.diginex.orderbook.OrderBook;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.CancelRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.request.Request;
import alick.diginex.orderbook.response.ErrorResponse;
import alick.diginex.orderbook.response.Execution;
import alick.diginex.orderbook.response.Response;
import alick.diginex.orderbook.response.SuccessResponse;
import alick.diginex.util.IdGenerator;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonList;

/**
 * Matching engine for a single instrument.
 *
 * Thread-safety: The exchange is thread-safe. Requests can be submitted from different thread in a blocking manner.
 */
@ToString(of = "instrumentName")
@Log4j2
public class MatchingEngine {
	/**
	 * @param <T> response type
	 */
	private static abstract class OrderBookAction<T>{
		private final CountDownLatch responseLatch = new CountDownLatch(1);
		protected T response;

		/**
		 * do things with the order book
		 */
		abstract void apply(OrderBook orderBook);

		/**
		 * set response object and notify any caller waiting for the response
		 */
		protected void setResponse(final T response) {
			this.response = response;
			this.responseLatch.countDown();
		}

		/**
		 * Wait, blocking, for the response to the request submitted.
		 */
		T waitForResponse() throws InterruptedException {
			this.responseLatch.await();
			return this.response;
		}
	}

	private static final class SubmitRequestOrderBookAction extends OrderBookAction<Response> {
		private final Request request;
		private Instant transactTime = null;

		private SubmitRequestOrderBookAction(final Request request) {
			this.request = request;
		}

		@Override
		void apply(final OrderBook orderBook) {
			final Response resp = orderBook.submitRequest(this.request);
			this.setResponse(resp, Instant.now());
		}

		/**
		 * set the response to the request.
		 */
		private void setResponse(final Response response, final Instant transactTime) {
			this.transactTime = transactTime;
			super.setResponse(response);
		}
	}

	private static final class SnapshotOrderBookAction extends OrderBookAction<OrderBookSnapshot> {
		@Override
		void apply(final OrderBook orderBook) {
			final OrderBookSnapshot snapshot = orderBook.snapshotOrderBook();
			super.setResponse(snapshot);
		}
	}

	private final LinkedBlockingQueue<OrderBookAction<?>> orderSubmissionQueue = new LinkedBlockingQueue<>();
	private final Thread orderBookProcessingThread;

	private final String instrumentName;
	private final OrderBook orderBook;
	private final IdGenerator idGenerator = new IdGenerator();

	private final LongObjectHashMap<ClientOrder> orderMap = new LongObjectHashMap<>();
	private final ObjectLongHashMap<String> clOrdId2orderIdMap = new ObjectLongHashMap<>();
	private final LinkedList<Trade> tradeHistory = new LinkedList<>();

	public MatchingEngine(final String instrumentName, final double referencePrice) {
		this.instrumentName = instrumentName;
		this.orderBook = new OrderBook(referencePrice);
		final String threadName = "OrderBook-Processing-" + this.instrumentName;
		this.orderBookProcessingThread = new Thread(threadName) {
			@Override
			public void run() {
				while (!Thread.currentThread().isInterrupted()) {
					try {
						final OrderBookAction<?> orderBookAction = MatchingEngine.this.orderSubmissionQueue.take();
						orderBookAction.apply(MatchingEngine.this.orderBook);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						log.info("{} thread interrupted. exit", threadName);
						// allow thread to exit safely
					}
				}
			}
		};
	}

	/**
	 * Starts the exchange, ready to receive orders
	 */
	public void start() {
		log.info("Starting Order-Processing-{}", this.instrumentName);
		this.orderBookProcessingThread.start();
	}

	/**
	 * Shuts down the exchange
	 */
	public void stop() {
		log.info("Stopping Order-Processing-{}", this.instrumentName);
		this.orderBookProcessingThread.interrupt();
	}

	public ClientOrder getOrderByClOrdId(final String clOrdId) {
		synchronized (orderMap) {
			final long orderId = this.clOrdId2orderIdMap.get(clOrdId);
			return (0 != orderId) ? getOrderByOrderId(orderId) : null;
		}
	}

	public ClientOrder getOrderByOrderId(final Long orderId) {
		synchronized (orderMap) {
			return this.orderMap.get(orderId);
		}
	}

	public List<Trade> getTradeHistory() {
		synchronized (this.orderMap) {
			return new ArrayList<>(this.tradeHistory);
		}
	}

	/**
	 * takes a snapshot of the order book.
	 * @return the snapshot
	 */
	public OrderBookSnapshot snapshotOrderBook() throws InterruptedException {
		final SnapshotOrderBookAction action = new SnapshotOrderBookAction();
		this.orderSubmissionQueue.add(action);
		return action.waitForResponse();
	}

	public List<ResponseMessage> submitNewOrderRequest(
			final String clOrdId,
			final Side side, final OrderType orderType,
			final double quantity, final double price) throws InterruptedException {
		final long orderId = this.idGenerator.getNextId();
		final ClientOrder clientOrder = ClientOrder.builder()
				.orderId(orderId).clOrdId(clOrdId)
				.side(side).orderType(orderType)
				.orderQty(quantity).price(price)
				.build();

		final NewRequest req;
		synchronized(orderMap) {
			if (this.clOrdId2orderIdMap.containsKey(clOrdId))
				return singletonList(ExecutionReport.builder()
						.clOrdId(clientOrder.getClOrdId()).origClOrdId(clientOrder.getOrigClOrdId()).orderId(clientOrder.getOrderId()).orderStatus(OrderStatus.REJECTED)
						.side(clientOrder.getSide()).orderType(clientOrder.getOrderType())
						.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
						.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
						.rejectReason("duplicated ClOrdId")
						.build());

			req = NewRequest.builder().orderId(orderId).side(side).orderType(orderType).quantity(quantity).price(price).build();
			this.orderMap.put(orderId, clientOrder);
			this.clOrdId2orderIdMap.put(clOrdId, orderId);
		}

		final SubmitRequestOrderBookAction action = new SubmitRequestOrderBookAction(req);
		this.orderSubmissionQueue.put(action);
		final Response response = action.waitForResponse();

		if (response instanceof ErrorResponse) {
			clientOrder.orderRejected();
			final ErrorResponse errorResp = (ErrorResponse) response;
			return singletonList(ExecutionReport.builder()
					.clOrdId(clientOrder.getClOrdId()).origClOrdId(clientOrder.getOrigClOrdId()).orderId(clientOrder.getOrderId()).orderStatus(clientOrder.getOrderStatus())
					.side(clientOrder.getSide()).orderType(clientOrder.getOrderType())
					.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
					.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
					.rejectReason(errorResp.getErrorMsg())
					.build());
		}

		final SuccessResponse successResp = (SuccessResponse) response;
		final List<Execution> executions = successResp.getExecutions();
		final ArrayList<ResponseMessage> responseMessages = new ArrayList<>(executions.size() + 1);
		responseMessages.add(ExecutionReport.builder()
				.clOrdId(clOrdId).orderId(orderId).orderStatus(OrderStatus.NEW)
				.side(side).orderType(orderType)
				.orderQty(quantity).price(price).cumQty(0).leavesQty(quantity).avgPx(0)
				.build());
		if (!executions.isEmpty()) {
			final Instant transactTime = action.transactTime;
			synchronized(orderMap) {
				for (final Execution execution : executions) {
					final double execQty = execution.getQuantity();
					final double tradePx = execution.getPrice();
					final Trade trade = Trade.builder().execQty(execQty).tradePx(tradePx).transactTime(transactTime).build();
					final ClientOrder buyOrder = this.orderMap.get(execution.getBuyOrderId());
					final ClientOrder sellOrder = this.orderMap.get(execution.getSellOrderId());
					if (null != buyOrder) {// purely being defensive
						buyOrder.addTrade(trade);
						final ExecutionReport er = generateExecutionReport(buyOrder, trade);
						responseMessages.add(er);
					}
					if (null != sellOrder) {// purely being defensive
						sellOrder.addTrade(trade);
						final ExecutionReport er = generateExecutionReport(sellOrder, trade);
						responseMessages.add(er);
					}
					this.tradeHistory.add(trade);
				}
			}
		}
		return responseMessages;
	}

	private static ExecutionReport generateExecutionReport(final ClientOrder order, final Trade trade) {
		return ExecutionReport.builder()
				.clOrdId(order.getClOrdId()).orderId(order.getOrderId()).orderStatus(order.getOrderStatus())
				.side(order.getSide()).orderType(order.getOrderType())
				.orderQty(order.getOrderQty()).price(order.getPrice())
				.cumQty(order.getCumQty()).leavesQty(order.getLeavesQty()).avgPx(order.getAvgPx())
				.lastQty(trade.getExecQty()).lastPx(trade.getTradePx())
				.build();
	}

	public List<ResponseMessage> submitAmendOrderRequest(
			final String origClOrdId, final String clOrdId,
			final Side side, final OrderType newOrderType,
			final double newQuantity, final double newPrice) throws InterruptedException {
		final ClientOrder clientOrder;
		final long orderId;
		final AmendRequest amendReq;
		synchronized (orderMap) {
			clientOrder = getOrderByClOrdId(origClOrdId);
			if (null == clientOrder)
				return singletonList(OrderCancelReject.builder().clOrdId(clOrdId).origClOrdId(origClOrdId).rejectReason("Unknown origClOrdId = " + origClOrdId).build());

			orderId = clientOrder.getOrderId();
			amendReq = AmendRequest.builder().orderId(orderId).side(side).orderType(newOrderType).newOrderQuantity(newQuantity).newPrice(newPrice).build();
			this.clOrdId2orderIdMap.put(clOrdId, clientOrder.getOrderId()); // now the map contains both old/new clOrdId -> order ID
		}

		final SubmitRequestOrderBookAction action = new SubmitRequestOrderBookAction(amendReq);
		this.orderSubmissionQueue.put(action);
		final Response response = action.waitForResponse();

		if (response instanceof ErrorResponse) {
			synchronized (orderMap) {
				this.clOrdId2orderIdMap.remove(clOrdId);
			}
			final ErrorResponse errorResp = (ErrorResponse) response;
			return singletonList(ExecutionReport.builder()
					.clOrdId(clOrdId).origClOrdId(origClOrdId).orderId(orderId).orderStatus(OrderStatus.REJECTED)
					.side(side).orderType(clientOrder.getOrderType())
					.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
					.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
					.rejectReason(errorResp.getErrorMsg())
					.build());
		}
		final SuccessResponse successResp = (SuccessResponse) response;
		clientOrder.orderAmended(
				clOrdId, origClOrdId,
				newOrderType,
				newQuantity, newPrice);

		final List<Execution> executions = successResp.getExecutions();
		final ArrayList<ResponseMessage> responseMessages = new ArrayList<>(executions.size() + 1);
		responseMessages.add(ExecutionReport.builder()
				.clOrdId(clientOrder.getClOrdId()).origClOrdId(clientOrder.getOrigClOrdId()).orderId(clientOrder.getOrderId()).orderStatus(OrderStatus.REPLACED)
				.side(clientOrder.getSide()).orderType(clientOrder.getOrderType())
				.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
				.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
				.build());
		if (!executions.isEmpty()) {
			final Instant transactTime = action.transactTime;
			synchronized(orderMap) {
				for (final Execution execution : executions) {
					final double execQty = execution.getQuantity();
					final double tradePx = execution.getPrice();
					final Trade trade = Trade.builder().execQty(execQty).tradePx(tradePx).transactTime(transactTime).build();
					final ClientOrder buyOrder = this.orderMap.get(execution.getBuyOrderId());
					final ClientOrder sellOrder = this.orderMap.get(execution.getSellOrderId());
					if (null != buyOrder) {// purely being defensive
						buyOrder.addTrade(trade);
						final ExecutionReport er = generateExecutionReport(buyOrder, trade);
						responseMessages.add(er);
					}
					if (null != sellOrder) {// purely being defensive
						sellOrder.addTrade(trade);
						final ExecutionReport er = generateExecutionReport(sellOrder, trade);
						responseMessages.add(er);
					}
					this.tradeHistory.add(trade);
				}
			}
		}
		return responseMessages;
	}

	public List<ResponseMessage> submitCancelOrderRequest(final String origClOrdId, final String clOrdId) throws InterruptedException {
		final ClientOrder clientOrder;
		final long orderId;
		final CancelRequest cancelReq;
		synchronized (orderMap) {
			clientOrder = getOrderByClOrdId(origClOrdId);
			if (null == clientOrder)
				return singletonList(OrderCancelReject.builder().clOrdId(clOrdId).origClOrdId(origClOrdId).rejectReason("Unknown origClOrdId = " + origClOrdId).build());

			orderId = clientOrder.getOrderId();
			cancelReq = CancelRequest.builder().orderId(orderId).build();
			this.clOrdId2orderIdMap.put(clOrdId, clientOrder.getOrderId()); // now the map contains both old/new clOrdId -> order ID
		}

		final SubmitRequestOrderBookAction action = new SubmitRequestOrderBookAction(cancelReq);
		this.orderSubmissionQueue.put(action);
		final Response response = action.waitForResponse();

		if (response instanceof ErrorResponse) {
			final ErrorResponse errorResp = (ErrorResponse) response;
			return singletonList(ExecutionReport.builder()
					.clOrdId(clientOrder.getClOrdId()).origClOrdId(clientOrder.getOrigClOrdId()).orderId(clientOrder.getOrderId()).orderStatus(clientOrder.getOrderStatus())
					.side(clientOrder.getSide()).orderType(clientOrder.getOrderType())
					.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
					.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
					.rejectReason(errorResp.getErrorMsg())
					.build());
		}

		clientOrder.orderCancelled(clOrdId, origClOrdId);
		return singletonList(ExecutionReport.builder()
						.clOrdId(clientOrder.getClOrdId()).origClOrdId(clientOrder.getOrigClOrdId()).orderId(clientOrder.getOrderId()).orderStatus(OrderStatus.CANCELLED)
						.side(clientOrder.getSide()).orderType(clientOrder.getOrderType())
						.orderQty(clientOrder.getOrderQty()).price(clientOrder.getPrice())
						.cumQty(clientOrder.getCumQty()).leavesQty(clientOrder.getLeavesQty()).avgPx(clientOrder.getAvgPx())
						.build());
	}
}
