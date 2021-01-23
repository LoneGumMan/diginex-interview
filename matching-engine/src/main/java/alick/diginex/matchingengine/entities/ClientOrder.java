package alick.diginex.matchingengine.entities;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ClientOrder {
	private final long orderId;
	private final Side side;
	private final LinkedList<Trade> executions = new LinkedList<>();

	private static class MutableOrderState {
		private final String clOrdId;
		private final String origClOrdId;
		private final OrderType orderType;

		private final double orderQty;
		private final double price;

		private final double leavesQty;
		private final double cumQty;
		private final double totalNotional;
		private final OrderStatus orderStatus;

		private MutableOrderState(
				final String clOrdId, final String origClOrdId,
				final OrderType orderType,
				final double orderQty, final double price,
				final double cumQty, final double leavesQty, final double totalNotional,
				final OrderStatus orderStatus) {
			this.clOrdId = clOrdId;
			this.origClOrdId = origClOrdId;
			this.orderType = orderType;
			this.orderQty = orderQty;
			this.price = price;
			this.leavesQty = leavesQty;
			this.cumQty = cumQty;
			this.totalNotional = totalNotional;
			this.orderStatus = orderStatus;
		}

		private double getAvgPx() {
			if (0 >= this.cumQty)
				return 0.0;
			return this.totalNotional / this.cumQty;
		}
	}

	private volatile MutableOrderState currentOrderState;

	public ClientOrder(final long orderId, final String clOrdId, final Side side, final OrderType orderType, final double orderQty, final double price) {
		this.orderId = orderId;
		this.side = side;
		this.currentOrderState = new MutableOrderState(clOrdId, null, orderType, orderQty, price, 0, orderQty, 0, OrderStatus.NEW);
	}

	public long getOrderId() {
		return this.orderId;
	}

	public Side getSide() {
		return this.side;
	}

	public String getClOrdId() {
		return this.currentOrderState.clOrdId;
	}

	public String getOrigClOrdId() {
		return this.currentOrderState.origClOrdId;
	}

	public OrderType getOrderType() {
		return this.currentOrderState.orderType;
	}

	public double getOrderQty() {
		return this.currentOrderState.orderQty;
	}

	public double getCumQty() {
		return this.currentOrderState.cumQty;
	}

	public double getLeavesQty() {
		return this.currentOrderState.leavesQty;
	}

	public double getAvgPx() {
		return this.currentOrderState.getAvgPx();
	}

	public double getPrice() {
		return this.currentOrderState.price;
	}

	public OrderStatus getOrderStatus() {
		return this.currentOrderState.orderStatus;
	}

	public synchronized List<Trade> getExecutions() {
		//need synchronization because this is not delegated to current order state
		return new ArrayList<>(this.executions);
	}

	public synchronized void addTrade(final Trade trade) {
		this.executions.add(trade);
		final double execQty = trade.getExecQty();
		final double execPx = trade.getTradePx();
		// capture so we don't reach through volatile on ever field
		final MutableOrderState curState = this.currentOrderState;
		final double updatedCumQty = curState.cumQty + execQty;
		final double updatedLeavesQty = curState.leavesQty - execQty;
		final double updatedTotalNotional = curState.totalNotional + execQty * execPx;
		// it is possible to over execute ... in general
		final OrderStatus updatedOrderStatus;
		if (updatedCumQty >= curState.orderQty)
			updatedOrderStatus = OrderStatus.FILLED;
		else
			updatedOrderStatus = OrderStatus.PARTIAL_FILLLED;

		this.currentOrderState = new MutableOrderState(
				curState.clOrdId, curState.origClOrdId,
				curState.orderType,
				curState.orderQty, curState.price,
				updatedCumQty, updatedLeavesQty, updatedTotalNotional,
				updatedOrderStatus);
	}

	public synchronized void orderAmended(final String clOrdId, final String origClOrdId, final OrderType amendedOrderType, final double amendedOrderQty, final double amendedPrice) {
		// capture so we don't reach through volatile on ever field
		final MutableOrderState curState = this.currentOrderState;
		final OrderStatus updatedOrderStatus;
		if (curState.cumQty >= amendedOrderQty)
			updatedOrderStatus = OrderStatus.FILLED;
		else if (curState.cumQty > 0)
			updatedOrderStatus = OrderStatus.PARTIAL_FILLLED;
		else
			updatedOrderStatus = OrderStatus.NEW;

		this.currentOrderState = new MutableOrderState(
				clOrdId, origClOrdId,
				amendedOrderType,
				amendedOrderQty, amendedPrice,
				curState.cumQty, curState.leavesQty, curState.totalNotional,
				updatedOrderStatus);
	}

	public synchronized void orderCancelled(final String clOrdId, final String origClOrdId) {
		// capture so we don't reach through volatile on ever field
		final MutableOrderState curState = this.currentOrderState;
		this.currentOrderState = new MutableOrderState(
				clOrdId, origClOrdId,
				curState.orderType,
				curState.orderQty, curState.price,
				curState.cumQty, 0, curState.totalNotional,
				OrderStatus.CANCELLED);
	}

	public synchronized void orderRejected() {
		final MutableOrderState curState = this.currentOrderState;
		this.currentOrderState = new MutableOrderState(
				curState.clOrdId, curState.origClOrdId,
				curState.orderType,
				curState.orderQty, curState.price,
				curState.cumQty, 0, curState.totalNotional,
				OrderStatus.REJECTED);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final ClientOrder that = (ClientOrder) o;
		return getOrderId() == that.getOrderId();
	}

	@Override
	public int hashCode() {
		return Objects.hash(getOrderId());
	}
}
