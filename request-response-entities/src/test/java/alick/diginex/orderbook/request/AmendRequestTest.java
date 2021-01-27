package alick.diginex.orderbook.request;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AmendRequestTest {
	private Random random;

	@BeforeEach
	public void setup() {
		this.random = new Random();
	}

	@Test
	public void badSideShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(null).orderType(OrderType.LIMIT).newOrderQuantity(10).newPrice(10d).build(),
				"null side");
	}

	@Test
	public void badOrderTypeShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(null).newOrderQuantity(10).newPrice(10d).build(),
				"null order type");
	}

	@Test
	public void zeroQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).newOrderQuantity(0).newPrice(10d).build(),
				"zero order quantity");
	}

	@Test
	public void negativeQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).newOrderQuantity(-1).newPrice(10d).build(),
				"negative order quantity");
	}

	@Test
	public void zeroPriceNonMarketOrderShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).newOrderQuantity(10).newPrice(0).build(),
				"negative order quantity");
	}

	@Test
	public void zeroPriceMarketOrderOK() {
		Assertions.assertDoesNotThrow(
				() -> AmendRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.MARKET).newOrderQuantity(10).newPrice(0)).build();
	}
}