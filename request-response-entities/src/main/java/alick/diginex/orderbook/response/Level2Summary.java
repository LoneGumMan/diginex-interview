package alick.diginex.orderbook.response;

import java.util.List;
import java.util.Objects;

public class Level2Summary {
	public static class PriceQuantity {
		private final double price;
		private final double quantity;

		public PriceQuantity(final double price, final double quantity) {
			this.price = price;
			this.quantity = quantity;
		}

		public double getPrice() {
			return price;
		}

		public double getQuantity() {
			return quantity;
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			final PriceQuantity that = (PriceQuantity) o;
			return Double.compare(that.getPrice(), getPrice()) == 0 && getQuantity() == that.getQuantity();
		}

		@Override
		public int hashCode() {
			return Objects.hash(getPrice(), getQuantity());
		}

		@Override
		public String toString() {
			return "PriceQuantity(" +
					"price=" + price +
					", quantity=" + quantity +
					')';
		}
	}

	private final List<PriceQuantity> depths;

	public Level2Summary(final List<PriceQuantity> depths) {
		this.depths = depths;
	}

	public List<PriceQuantity> getDepths() {
		return depths;
	}
}
