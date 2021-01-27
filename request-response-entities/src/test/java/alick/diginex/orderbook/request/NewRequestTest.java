package alick.diginex.orderbook.request;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NewRequestTest {
	private Random random;

	@BeforeEach
	public void setup() {
		this.random = new Random();
	}

	@Test
	public void badSideShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> NewRequest.builder().orderId(this.random.nextLong()).orderType(OrderType.LIMIT).quantity(10).price(10).build(),
				"null side");
	}

	@Test
	public void badOrderTypeShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> NewRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).quantity(10).price(10).build(),
				"null order type");
	}

	@Test
	public void zeroQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> NewRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(0).price(10).build(),
				"zero order quantity");
	}

	@Test
	public void negativeQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> NewRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(-1).price(10).build(),
				"negative order quantity");
	}

	@Test
	public void zeroPriceNonMarketOrderShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> NewRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(10).price(0).build(),
				"negative order quantity");
	}

	@Test
	public void zeroPriceMarketOrderOK() {
		Assertions.assertDoesNotThrow(
				() -> NewRequest.builder().orderId(this.random.nextLong()).side(Side.BUY).orderType(OrderType.MARKET).quantity(10).price(0).build());
	}
}