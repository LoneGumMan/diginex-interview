package alick.diginex.orderbook;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.response.*;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class OrderBookAmendTest extends OrderBookTestBase {
	@Test
	public void amendNonExistentOrder() {
		final NewRequest newRequest = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest);
			assertThat("new order action successful", resp1, instanceOf(SuccessResponse.class));
		}

		final long orderId = idGenerator.getNextId();
		final AmendRequest request = new AmendRequest(orderId, Side.BUY, OrderType.LIMIT, 100, 99.8);
		{
			final Response response = this.orderBook.submitRequest(request);

			assertThat("expects error for amending non-existent order", response, instanceOf(ErrorResponse.class));
			final ErrorResponse errorResp = (ErrorResponse) response;

			assertThat("order ID should be same as passed in", errorResp.getOrderId(), equalTo(orderId));
			assertThat("bid market depth after failed amend", errorResp.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after failed amend", errorResp.getAskSummary().getDepths(), empty());
			assertThat("bid summary after failed amend", errorResp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(newRequest.getPrice(), newRequest.getQuantity())));
			assertThat("no executions on error", errorResp.getExecutions(), empty());
		}
	}

	@Test
	public void amendDownQuantity() {
		final NewRequest newRequest1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		final NewRequest newRequest2 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);

		{
			final Response resp1 = this.orderBook.submitRequest(newRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(newRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			final SuccessResponse successResp = (SuccessResponse) resp2;
			assertThat("ask market depth after new orders", successResp.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", successResp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(newRequest1.getPrice(), newRequest1.getQuantity() + newRequest2.getQuantity())));
		}
		final AmendRequest amendOrder1 = new AmendRequest(newRequest1.getOrderId(), newRequest1.getSide(), newRequest1.getOrderType(), 50, newRequest1.getPrice());
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));

			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(newRequest1.getPrice(), amendOrder1.getNewOrderQuantity() + newRequest2.getQuantity())));
		}
	}

	@Test
	public void amendUpQuantity() {
		final NewRequest newRequest1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		final NewRequest newRequest2 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(newRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			assertThat("ask market depth after new orders", resp2.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", resp2.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(newRequest1.getPrice(), newRequest1.getQuantity() + newRequest2.getQuantity())));
		}

		final AmendRequest amendOrder1 = new AmendRequest(newRequest1.getOrderId(), newRequest1.getSide(), newRequest1.getOrderType(), 123, newRequest1.getPrice());
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));
			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(newRequest1.getPrice(), amendOrder1.getNewOrderQuantity() + newRequest2.getQuantity())));
		}
	}

	@Test
	public void amendUpQuantityChangesExecutionOrder() {
		final NewRequest buyRequest1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 110, 99.9);
		final NewRequest buyRequest2 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		{
			final Response resp1 = this.orderBook.submitRequest(buyRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(buyRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			assertThat("bid market depth after new orders", resp2.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after new orders", resp2.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", resp2.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(buyRequest1.getPrice(), 110 + 100)));
		}

		// originally, order 1 gets crossed first
		final NewRequest sellReq0 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 10, 99.9);
		{
			final Response sellResp0 = this.orderBook.submitRequest(sellReq0);
			assertThat("new sell order 0 successful", sellResp0, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 0", sellResp0.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.9, (110 - 10) + 100)));
			assertThat("ask summary after sell 1", sellResp0.getAskSummary().getDepths(), empty());
			final SuccessResponse success0 = (SuccessResponse) sellResp0;
			assertThat("execution 1", success0.getExecutions(), containsInAnyOrder(
					new Execution(buyRequest1.getOrderId(), sellReq0.getOrderId(), 10, sellReq0.getPrice())
			));
		}
		final AmendRequest amendOrder1 = new AmendRequest(buyRequest1.getOrderId(),buyRequest1.getSide(),buyRequest1.getOrderType(), 110 - 10 + 23, buyRequest1.getPrice());
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));

			assertThat("bid market depth after successful amend", amendResp1.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(buyRequest1.getPrice(), 100 + 123)));
		}
		// cross now, order 2 should get executed before order 1
		final NewRequest sellReq1 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 80, 99.9);
		{
			final Response sellResp1 = this.orderBook.submitRequest(sellReq1);
			assertThat("new sell order 1 successful", sellResp1, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 1", sellResp1.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.9, (100 - 80) + 123)));
			assertThat("ask summary after sell 1", sellResp1.getAskSummary().getDepths(), empty());
			final SuccessResponse success1 = (SuccessResponse) sellResp1;
			assertThat("execution 1", success1.getExecutions(), containsInAnyOrder(
					new Execution(buyRequest2.getOrderId(), sellReq1.getOrderId(), 80, sellReq1.getPrice())
			));
		}
		final NewRequest sellReq2 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 45, 99.9);
		{
			final Response sellResp2 = this.orderBook.submitRequest(sellReq2);
			assertThat("new sell order 2 successful", sellResp2, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 1", sellResp2.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.9, (100 - 80 - 20) + (123 - 25))));
			assertThat("ask summary after sell 1", sellResp2.getAskSummary().getDepths(), empty());
			final SuccessResponse success2 = (SuccessResponse) sellResp2;
			// finish crossing with order 2 first, then order 1
			assertThat("execution 2", success2.getExecutions(), containsInAnyOrder(
					new Execution(buyRequest2.getOrderId(), sellReq2.getOrderId(), 20, sellReq2.getPrice()),
					new Execution(buyRequest1.getOrderId(), sellReq2.getOrderId(), 25, sellReq2.getPrice())
			));
		}
	}

	@Test
	public void amendPriceCanTriggerExecution() {
		final NewRequest buyRequest1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		final NewRequest buyRequest2 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 200, 99.9);
		final NewRequest sellRequest3 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 140, 100.5);
		{
			final Response resp1 = this.orderBook.submitRequest(buyRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(buyRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));
			final Response resp3 = this.orderBook.submitRequest(sellRequest3);
			assertThat("new order 3 action successful", resp3, instanceOf(SuccessResponse.class));

			final SuccessResponse resp = (SuccessResponse) resp3;
			assertThat("bid summary after new orders", resp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(buyRequest1.getPrice(), 100 + 200)));
			assertThat("ask summary after new orders", resp.getAskSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(sellRequest3.getPrice(), 140)));
			assertThat("executions after new order", resp.getExecutions(), empty());
		}
		// just amend price
		final AmendRequest amendReq4 = new AmendRequest(buyRequest2.getOrderId(), buyRequest2.getSide(), buyRequest2.getOrderType(), buyRequest2.getQuantity(), sellRequest3.getPrice());
		{
			final Response resp4 = this.orderBook.submitRequest(amendReq4);
			assertThat("amend action successful", resp4, instanceOf(SuccessResponse.class));
			final SuccessResponse resp = (SuccessResponse) resp4;
			assertThat("bid summary after amend price", resp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(amendReq4.getNewPrice(), 200 - 140),
					new Level2Summary.PriceQuantity(buyRequest1.getPrice(), 100)
			));
			assertThat("ask summary after amend price", resp.getAskSummary().getDepths(), empty());

			assertThat("executions after amend price", resp.getExecutions(), containsInAnyOrder(
					new Execution(buyRequest2.getOrderId(), sellRequest3.getOrderId(), 140, sellRequest3.getPrice())
			));
		}
	}
}
