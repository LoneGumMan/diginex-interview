package alick.diginex.matchingengine.entities;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@EqualsAndHashCode(of="orderId")
public class ClientOrder {
	@Getter
	private final long orderId;
	@Getter
	private final Side side;
	private final LinkedList<Trade> executions = new LinkedList<>();

	@Getter
	@lombok.Builder(builderClassName = "Builder")
	private static class MutableOrderState {
		@NonNull
		private final String clOrdId;

		private final String origClOrdId;
		@NonNull
		private final OrderType orderType;

		private final double orderQty;
		private final double price;

		private final double leavesQty;
		private final double cumQty;
		private final double totalNotional;
		@NonNull
		private final OrderStatus orderStatus;

		private double getAvgPx() {
			if (0 >= this.cumQty)
				return 0.0;
			return this.totalNotional / this.cumQty;
		}
	}

	private volatile MutableOrderState currentOrderState;

	@lombok.Builder(builderClassName = "Builder")
	private ClientOrder(final long orderId, @NonNull final String clOrdId, @NonNull final Side side, @NonNull final OrderType orderType, final double orderQty, final double price) {
		this.orderId = orderId;
		this.side = side;
		this.currentOrderState = MutableOrderState.builder()
				.clOrdId(clOrdId).orderType(orderType)
				.orderQty(orderQty).price(price)
				.cumQty(0).leavesQty(orderQty).totalNotional(0)
				.orderStatus(OrderStatus.NEW)
				.build();
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
			updatedOrderStatus = OrderStatus.PARTIAL_FILLED;

		this.currentOrderState = MutableOrderState.builder()
				.clOrdId(curState.getClOrdId()).origClOrdId(curState.getOrigClOrdId())
				.orderType(curState.getOrderType())
				.orderQty(curState.getOrderQty()).price(curState.getPrice())
				.cumQty(updatedCumQty).leavesQty(updatedLeavesQty).totalNotional(updatedTotalNotional)
				.orderStatus(updatedOrderStatus)
				.build();
	}

	public synchronized void orderAmended(final String clOrdId, final String origClOrdId, final OrderType amendedOrderType, final double amendedOrderQty, final double amendedPrice) {
		// capture so we don't reach through volatile on ever field
		final MutableOrderState curState = this.currentOrderState;
		final OrderStatus updatedOrderStatus;
		if (curState.cumQty >= amendedOrderQty)
			updatedOrderStatus = OrderStatus.FILLED;
		else if (curState.cumQty > 0)
			updatedOrderStatus = OrderStatus.PARTIAL_FILLED;
		else
			updatedOrderStatus = OrderStatus.NEW;

		this.currentOrderState = MutableOrderState.builder()
				.clOrdId(clOrdId).origClOrdId(origClOrdId)
				.orderType(amendedOrderType)
				.orderQty(amendedOrderQty).price(amendedPrice)
				.cumQty(curState.getCumQty()).leavesQty(curState.getLeavesQty()).totalNotional(curState.getTotalNotional())
				.orderStatus(updatedOrderStatus)
				.build();
	}

	public synchronized void orderCancelled(final String clOrdId, final String origClOrdId) {
		// capture so we don't reach through volatile on ever field
		final MutableOrderState curState = this.currentOrderState;
		this.currentOrderState = MutableOrderState.builder()
				.clOrdId(clOrdId).origClOrdId(origClOrdId)
				.orderType(curState.getOrderType())
				.orderQty(curState.getOrderQty()).price(curState.getPrice())
				.cumQty(curState.getCumQty()).leavesQty(0).totalNotional(curState.getTotalNotional())
				.orderStatus(OrderStatus.CANCELLED)
				.build();
	}

	public synchronized void orderRejected() {
		final MutableOrderState curState = this.currentOrderState;
		this.currentOrderState = MutableOrderState.builder()
				.clOrdId(curState.getClOrdId()).origClOrdId(curState.getOrigClOrdId())
				.orderType(curState.getOrderType())
				.orderQty(curState.getOrderQty()).price(curState.getPrice())
				.cumQty(curState.getCumQty()).leavesQty(0).totalNotional(curState.getTotalNotional())
				.orderStatus(OrderStatus.REJECTED)
				.build();
	}
}
