package alick.diginex.orderbook.response;

import lombok.*;

import java.util.List;

@Getter
@lombok.Builder(builderClassName = "Builder")
public class Level2Summary {
	@Getter
	@EqualsAndHashCode(of = {"price", "quantity"})
	@ToString(of = {"price", "quantity"})
	@lombok.Builder(builderClassName = "Builder")
	public static class PriceQuantity {
		private final double price;
		private final double quantity;
	}

	@Singular
	private final List<PriceQuantity> depths;
}
