package alick.diginex.orderbook;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class OrderBucketTest {
	@Test
	public void matchExactlyOneOrderFromQueue() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1500.0d));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 100);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(100.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(1));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(new OrderBucket.MatchedOrder(1,100)));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1500.0d - 100));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(5, 500),
						new OrderEntry(4, 400),
						new OrderEntry(3, 300),
						new OrderEntry(2, 200)
				));
	}

	@Test
	public void matchMoreThanOneOrder() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1500.0d));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 400);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(400.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(3));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(
						new OrderBucket.MatchedOrder(3, 100),
						new OrderBucket.MatchedOrder(2, 200),
						new OrderBucket.MatchedOrder(1, 100)
				));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1500.0d - 400));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(4, 400),
						new OrderEntry(5, 500),
						new OrderEntry(3, 200)
				));
	}

	@Test
	public void partialMatchOneOrder() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1400.0d));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 150);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(150.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(1));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(new OrderBucket.MatchedOrder(2, 150)));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1400.0d - 150));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(4, 400),
						new OrderEntry(5, 500),
						new OrderEntry(3, 200),
						new OrderEntry(2, 50)
				));
	}

	@Test
	public void matchesMustBeInOrder() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1500.0d));
		assertThat("order of order entry", bucket.getOrderEntryList(),
				Matchers.contains(
						new OrderEntry(4, 400),
						new OrderEntry(2, 200),
						new OrderEntry(1, 100),
						new OrderEntry(3, 300),
						new OrderEntry(5, 500)
				));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 500);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(500.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(2));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(
						new OrderBucket.MatchedOrder(2, 100),
						new OrderBucket.MatchedOrder(4, 400)));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1500.0d - 500));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(5, 500),
						new OrderEntry(3, 300),
						new OrderEntry(2, 100),
						new OrderEntry(1, 100)
				));
	}

	@Test
	public void matchExactQuantityInTheBucket() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1500.0d));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 1500);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(1500.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(5));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(
						new OrderBucket.MatchedOrder(2, 200),
						new OrderBucket.MatchedOrder(1, 100),
						new OrderBucket.MatchedOrder(4, 400),
						new OrderBucket.MatchedOrder(3, 300),
						new OrderBucket.MatchedOrder(5, 500)));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(0.0d));
		assertThat("queue should not be empty", bucket.isEmpty(), is(true));
		assertThat("remaining orders in bucket", bucket.getOrderEntryList(), empty());
	}

	@Test
	public void moreQuantityThanInTheBucket() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		assertThat("initial quantity in queue", bucket.getQuantityInQueue(), is(1500.0d));

		final OrderEntry oppositeOrderEntry = new OrderEntry(-1, 1680);
		final MatchResult matchResult = bucket.matchOrder(oppositeOrderEntry);
		assertThat("matched execute result", matchResult.getTotalMatchedQuantity(), is(1500.0d));
		assertThat("number of matched orders", matchResult.getMatchedOrders(), hasSize(5));

		assertThat("matched order",
				matchResult.getMatchedOrders(),
				containsInAnyOrder(
						new OrderBucket.MatchedOrder(1, 100),
						new OrderBucket.MatchedOrder(4, 400),
						new OrderBucket.MatchedOrder(5, 500),
						new OrderBucket.MatchedOrder(3, 300),
						new OrderBucket.MatchedOrder(2, 200)));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(0.0d));
		assertThat("queue should not be empty", bucket.isEmpty(), is(true));
		assertThat("remaining orders in bucket", bucket.getOrderEntryList(), empty());
	}

	@Test
	public void cancelOrderSuccess() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		final long orderIdToCancel = 4L;

		assertThat("order cancel successful", bucket.cancelOrder(orderIdToCancel), is(true));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1500.0d - 400));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(5, 500),
						new OrderEntry(3, 300),
						new OrderEntry(2, 200),
						new OrderEntry(1, 100)
				));
	}

	@Test
	public void cancelOrderNotInBucket() {
		final OrderBucket bucket = OrderBucket.builder().priceOfBucket(100.0).build();

		bucket.enqueueOrder(new OrderEntry(1, 100));
		bucket.enqueueOrder(new OrderEntry(2, 200));
		bucket.enqueueOrder(new OrderEntry(3, 300));
		bucket.enqueueOrder(new OrderEntry(4, 400));
		bucket.enqueueOrder(new OrderEntry(5, 500));

		final long orderIdToCancel = 8L;

		assertThat("cannot cancel non-existent order", bucket.cancelOrder(orderIdToCancel), is(false));

		assertThat("queue should not be empty", bucket.getQuantityInQueue(), is(1500.0d));
		assertThat("queue should not be empty", bucket.isEmpty(), is(false));
		assertThat("remaining orders in bucket",
				bucket.getOrderEntryList(),
				Matchers.containsInAnyOrder(
						new OrderEntry(5, 500),
						new OrderEntry(3, 300),
						new OrderEntry(4, 400),
						new OrderEntry(2, 200),
						new OrderEntry(1, 100)
				));
	}
}
