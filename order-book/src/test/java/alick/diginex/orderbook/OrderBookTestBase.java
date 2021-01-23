package alick.diginex.orderbook;

import alick.diginex.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;

abstract class OrderBookTestBase {
	protected final IdGenerator idGenerator = new IdGenerator();
	protected OrderBook orderBook;

	@BeforeEach
	public void setup() {
		this.orderBook = new OrderBook(100);
	}
}
