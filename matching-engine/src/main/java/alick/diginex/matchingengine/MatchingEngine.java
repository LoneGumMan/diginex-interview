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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonList;

/**
 * Matching engine for a single instrument.
 *
 * Thread-safety: The exchange is thread-safe. Requests can be submitted from different thread in a blocking manner.
 */
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

	private final HashMap<Long, ClientOrder> orderMap = new HashMap<>();
	private final HashMap<String, Long> clOrdId2orderIdMap = new HashMap<>();
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
						final OrderBookAction orderBookAction = MatchingEngine.this.orderSubmissionQueue.take();
						orderBookAction.apply(MatchingEngine.this.orderBook);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						System.out.println(threadName + " thread interrupted. exit");
						// allow thread to exit safely
					}
				}
			}
		};
	}

	@Override
	public String toString() {
		return "Exchange(" +
				"instrumentName='" + instrumentName + '\'' +
				')';
	}

	/**
	 * Starts the exchange, ready to receive orders
	 */
	public void start() {
		System.out.printf("Starting Order-Processing-" + this.instrumentName + "%n");
		this.orderBookProcessingThread.start();
	}

	/**
	 * Shuts down the exchange
	 */
	public void stop() {
		System.out.printf("Stopping Order-Processing-" + this.instrumentName + "%n");
		this.orderBookProcessingThread.interrupt();
	}

	public ClientOrder getOrderByClOrdId(final String clOrdId) {
		synchronized (orderMap) {
			final Long orderId = this.clOrdId2orderIdMap.get(clOrdId);
			return (null != orderId) ? getOrderByOrderId(orderId) : null;
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
		final OrderBookSnapshot response = action.waitForResponse();
		return response;
	}

	public List<ResponseMessage> submitNewOrderRequest(
			final String clOrdId,
			final Side side, final OrderType orderType,
			final double quantity, final double price) throws InterruptedException {
		final long orderId = this.idGenerator.getNextId();
		final ClientOrder clientOrder = new ClientOrder(orderId, clOrdId, side, orderType, quantity, price);
		final NewRequest req;
		synchronized(orderMap) {
			if (this.clOrdId2orderIdMap.containsKey(clOrdId))
				return singletonList(new ExecutionReport(
						clientOrder.getClOrdId(), clientOrder.getOrigClOrdId(), clientOrder.getOrderId(), OrderStatus.REJECTED,
						clientOrder.getSide(), clientOrder.getOrderType(),
						clientOrder.getOrderQty(), clientOrder.getPrice(),
						clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
						null, null,
						"duplicated ClOrdId"));

			req = new NewRequest(orderId, side, orderType, quantity, price);
			this.orderMap.put(orderId, clientOrder);
			this.clOrdId2orderIdMap.put(clOrdId, orderId);
		}

		final SubmitRequestOrderBookAction action = new SubmitRequestOrderBookAction(req);
		this.orderSubmissionQueue.put(action);
		final Response response = action.waitForResponse();

		if (response instanceof ErrorResponse) {
			clientOrder.orderRejected();
			final ErrorResponse errorResp = (ErrorResponse) response;
			return singletonList(new ExecutionReport(
					clientOrder.getClOrdId(), clientOrder.getOrigClOrdId(), clientOrder.getOrderId(), clientOrder.getOrderStatus(),
					clientOrder.getSide(), clientOrder.getOrderType(),
					clientOrder.getOrderQty(), clientOrder.getPrice(),
					clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
					null, null,
					errorResp.getErrorMsg()));
		}

		final SuccessResponse successResp = (SuccessResponse) response;
		final List<Execution> executions = successResp.getExecutions();
		final ArrayList<ResponseMessage> responseMessages = new ArrayList<>(executions.size() + 1);
		responseMessages.add(new ExecutionReport(clOrdId, null, orderId, OrderStatus.NEW, side, orderType, quantity, price, 0, quantity, 0, null, null));
		if (!executions.isEmpty()) {
			final Instant transactTime = action.transactTime;
			synchronized(orderMap) {
				for (final Execution execution : executions) {
					final double execQty = execution.getQuantity();
					final double tradePx = execution.getPrice();
					final Trade trade = new Trade(execQty, tradePx, transactTime);
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
		return new ExecutionReport(
				order.getClOrdId(), null, order.getOrderId(), order.getOrderStatus(),
				order.getSide(), order.getOrderType(),
				order.getOrderQty(), order.getPrice(),
				order.getCumQty(), order.getLeavesQty(), order.getAvgPx(),
				trade.getExecQty(), trade.getTradePx());
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
				return singletonList(new OrderCancelReject(clOrdId, origClOrdId, "Unknown origClOrdId = " + origClOrdId));

			orderId = clientOrder.getOrderId();
			amendReq = new AmendRequest(orderId, side, newOrderType, newQuantity, newPrice);
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
			return singletonList(new ExecutionReport(
					clOrdId, origClOrdId, orderId, OrderStatus.REJECTED,
					side, clientOrder.getOrderType(),
					clientOrder.getOrderQty(), clientOrder.getPrice(),
					clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
					null, null,
					errorResp.getErrorMsg()));
		}
		final SuccessResponse successResp = (SuccessResponse) response;
		clientOrder.orderAmended(
				clOrdId, origClOrdId,
				newOrderType,
				newQuantity, newPrice);

		final List<Execution> executions = successResp.getExecutions();
		final ArrayList<ResponseMessage> responseMessages = new ArrayList<>(executions.size() + 1);
		responseMessages.add(new ExecutionReport(
				clientOrder.getClOrdId(), clientOrder.getOrigClOrdId(), clientOrder.getOrderId(), OrderStatus.REPLACED,
				clientOrder.getSide(), clientOrder.getOrderType(),
				clientOrder.getOrderQty(), clientOrder.getPrice(),
				clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
				null, null));
		if (!executions.isEmpty()) {
			final Instant transactTime = action.transactTime;
			synchronized(orderMap) {
				for (final Execution execution : executions) {
					final double execQty = execution.getQuantity();
					final double tradePx = execution.getPrice();
					final Trade trade = new Trade(execQty, tradePx, transactTime);
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
				return singletonList(new OrderCancelReject(clOrdId, origClOrdId, "Unknown origClOrdId = " + origClOrdId));

			orderId = clientOrder.getOrderId();
			cancelReq = new CancelRequest(orderId);
			this.clOrdId2orderIdMap.put(clOrdId, clientOrder.getOrderId()); // now the map contains both old/new clOrdId -> order ID
		}

		final SubmitRequestOrderBookAction action = new SubmitRequestOrderBookAction(cancelReq);
		this.orderSubmissionQueue.put(action);
		final Response response = action.waitForResponse();

		if (response instanceof ErrorResponse) {
			final ErrorResponse errorResp = (ErrorResponse) response;
			return singletonList(new ExecutionReport(
					clientOrder.getClOrdId(), clientOrder.getOrigClOrdId(), clientOrder.getOrderId(), clientOrder.getOrderStatus(),
					clientOrder.getSide(), clientOrder.getOrderType(),
					clientOrder.getOrderQty(), clientOrder.getPrice(),
					clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
					null, null,
					errorResp.getErrorMsg()));
		}

		clientOrder.orderCancelled(clOrdId, origClOrdId);
		return singletonList(
				new ExecutionReport(
						clientOrder.getClOrdId(), clientOrder.getOrigClOrdId(), clientOrder.getOrderId(), OrderStatus.CANCELLED,
						clientOrder.getSide(), clientOrder.getOrderType(),
						clientOrder.getOrderQty(), clientOrder.getPrice(),
						clientOrder.getCumQty(), clientOrder.getLeavesQty(), clientOrder.getAvgPx(),
						null, null));
	}
}
