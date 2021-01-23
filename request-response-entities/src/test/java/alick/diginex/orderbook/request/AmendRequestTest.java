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
				() -> new AmendRequest(this.random.nextLong(), null, OrderType.LIMIT, 10, 10d),
				"null side");
	}

	@Test
	public void badOrderTypeShouldThrow() {
		assertThrows(NullPointerException.class,
				() -> new AmendRequest(this.random.nextLong(), Side.BUY, null, 10, 10d),
				"null order type");
	}

	@Test
	public void zeroQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new AmendRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, 0, 10d),
				"zero order quantity");
	}

	@Test
	public void negativeQuantityShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new AmendRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, -1, 10d),
				"negative order quantity");
	}

	@Test
	public void zeroPriceNonMarketOrderShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> new AmendRequest(this.random.nextLong(), Side.BUY, OrderType.LIMIT, 10, 0),
				"negative order quantity");
	}

	@Test
	public void zeroPriceMarketOrderOK() {
		Assertions.assertDoesNotThrow(
				() -> new AmendRequest(this.random.nextLong(), Side.BUY, OrderType.MARKET, 10, 0));
	}
}