package alick.diginex.matchingengine.message;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.matchingengine.entities.OrderStatus;

/**
 * FIX-like execution report, for acknowledgement, fills, etc.
 */
public class ExecutionReport implements ResponseMessage {
	private final String clOrdId;
	private final String origClOrdId;
	private final long orderId;
	private final OrderStatus orderStatus;

	private final Side side;
	private final OrderType orderType;

	private final double orderQty;
	private final double price;
	private final double cumQty;
	private final double leavesQty;
	private final double avgPx;

	private final Double lastQty;
	private final Double lastPx;

	private final String rejectReason;

	public ExecutionReport(
			final String clOrdId,
			final String origClOrdId,
			final long orderId,
			final OrderStatus orderStatus,
			final Side side,
			final OrderType orderType,
			final double orderQty,
			final double price,
			final double cumQty,
			final double leavesQty,
			final double avgPx,
			final Double lastQty,
			final Double lastPx) {
		this(clOrdId, origClOrdId, orderId, orderStatus, side, orderType, orderQty, price, cumQty, leavesQty, avgPx, lastQty, lastPx, null);
	}

	public ExecutionReport(
			final String clOrdId,
			final String origClOrdId,
			final long orderId,
			final OrderStatus orderStatus,
			final Side side,
			final OrderType orderType,
			final double orderQty,
			final double price,
			final double cumQty,
			final double leavesQty,
			final double avgPx,
			final Double lastQty,
			final Double lastPx,
			final String rejectReason) {
		this.clOrdId = clOrdId;
		this.origClOrdId = origClOrdId;
		this.orderId = orderId;
		this.orderStatus = orderStatus;
		this.side = side;
		this.orderType = orderType;
		this.orderQty = orderQty;
		this.price = price;
		this.cumQty = cumQty;
		this.leavesQty = leavesQty;
		this.avgPx = avgPx;
		this.lastQty = lastQty;
		this.lastPx = lastPx;
		this.rejectReason = rejectReason;
	}

	@Override
	public MessageType getMessageType() {
		return MessageType.EXECUTION_REPORT;
	}

	public String getClOrdId() {
		return clOrdId;
	}

	public String getOrigClOrdId() {
		return origClOrdId;
	}

	public long getOrderId() {
		return orderId;
	}

	public OrderStatus getOrderState() {
		return orderStatus;
	}

	public Side getSide() {
		return side;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public double getOrderQty() {
		return orderQty;
	}

	public double getPrice() {
		return price;
	}

	public double getCumQty() {
		return cumQty;
	}

	public double getLeavesQty() {
		return leavesQty;
	}

	public double getAvgPx() {
		return avgPx;
	}

	public Double getLastQty() {
		return lastQty;
	}

	public Double getLastPx() {
		return lastPx;
	}

	public String getRejectReason() {
		return rejectReason;
	}
}
