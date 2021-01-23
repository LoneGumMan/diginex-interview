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
				() -> new NewRequest(this.random.nextLong(), null, OrderType.LIMIT, 10, 10),
				"null side");
	}

	@Test
	public void badOrderTypeShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> new NewRequest(this.random.nextLong(), Side.BUY, null, 10, 10),
				"null order type");
	}

	@Test
	public void zeroQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new NewRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, 0, 10),
				"zero order quantity");
	}

	@Test
	public void negativeQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new NewRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, -1, 10),
				"negative order quantity");
	}

	@Test
	public void zeroPriceNonMarketOrderShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new NewRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, 10, 0),
				"negative order quantity");
	}

	@Test
	public void zeroPriceMarketOrderOK() {
		Assertions.assertDoesNotThrow(
				() -> new NewRequest(this.random.nextLong(), Side.BUY, OrderType.MARKET, 10, 0));
	}
}