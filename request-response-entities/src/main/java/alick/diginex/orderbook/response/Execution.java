package alick.diginex.orderbook.response;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@lombok.Builder
@Getter
@EqualsAndHashCode(of = {"buyOrderId", "sellOrderId", "quantity", "price"})
@ToString(of = {"buyOrderId", "sellOrderId", "quantity", "price"})
public class Execution {
	private final long buyOrderId;
	private final long sellOrderId;

	private final double quantity;
	private final double price;
}