package alick.diginex.orderbook.response;

import java.util.Objects;

public class Execution {
	private final long buyOrderId;
	private final long sellOrderId;

	private final double quantity;
	private final double price;

	public Execution(final long buyOrderId, final long sellOrderId, final double quantity, final double price) {
		this.buyOrderId = buyOrderId;
		this.sellOrderId = sellOrderId;
		this.quantity = quantity;
		this.price = price;
	}

	public long getBuyOrderId() {
		return buyOrderId;
	}

	public long getSellOrderId() {
		return sellOrderId;
	}

	public double getQuantity() {
		return quantity;
	}

	public double getPrice() {
		return price;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final Execution execution = (Execution) o;
		return getBuyOrderId() == execution.getBuyOrderId()
				&& getSellOrderId() == execution.getSellOrderId()
				&& getQuantity() == execution.getQuantity()
				&& Double.compare(execution.getPrice(), getPrice()) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getBuyOrderId(), getSellOrderId(), getQuantity(), getPrice());
	}

	@Override
	public String toString() {
		return "Execution(" +
				"buyOrderId=" + buyOrderId +
				", sellOrderId=" + sellOrderId +
				", quantity=" + quantity +
				", price=" + price +
				')';
	}
}