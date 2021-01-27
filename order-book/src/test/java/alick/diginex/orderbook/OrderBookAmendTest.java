package alick.diginex.orderbook;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.response.*;
import alick.diginex.orderbook.response.Level2Summary.PriceQuantity;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class OrderBookAmendTest extends OrderBookTestBase {
	@Test
	public void amendNonExistentOrder() {
		final NewRequest newRequest = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest);
			assertThat("new order action successful", resp1, instanceOf(SuccessResponse.class));
		}

		final long orderId = idGenerator.getNextId();
		final AmendRequest request = AmendRequest.builder().orderId(orderId).side(Side.BUY).orderType(OrderType.LIMIT).newOrderQuantity(100).newPrice(99.8).build();
		{
			final Response response = this.orderBook.submitRequest(request);

			assertThat("expects error for amending non-existent order", response, instanceOf(ErrorResponse.class));
			final ErrorResponse errorResp = (ErrorResponse) response;

			assertThat("order ID should be same as passed in", errorResp.getOrderId(), equalTo(orderId));
			assertThat("bid market depth after failed amend", errorResp.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after failed amend", errorResp.getAskSummary().getDepths(), empty());
			assertThat("bid summary after failed amend", errorResp.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(newRequest.getPrice()).quantity(newRequest.getQuantity()).build()));
			assertThat("no executions on error", errorResp.getExecutions(), empty());
		}
	}

	@Test
	public void amendDownQuantity() {
		final NewRequest newRequest1 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		final NewRequest newRequest2 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();

		{
			final Response resp1 = this.orderBook.submitRequest(newRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(newRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			final SuccessResponse successResp = (SuccessResponse) resp2;
			assertThat("ask market depth after new orders", successResp.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", successResp.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(newRequest1.getPrice()).quantity(newRequest1.getQuantity() + newRequest2.getQuantity()).build()));
		}
		final AmendRequest amendOrder1 = AmendRequest.builder().orderId(newRequest1.getOrderId()).side(newRequest1.getSide()).orderType(newRequest1.getOrderType()).newOrderQuantity(50).newPrice(newRequest1.getPrice()).build();
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));

			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(newRequest1.getPrice()).quantity(amendOrder1.getNewOrderQuantity() + newRequest2.getQuantity()).build()));
		}
	}

	@Test
	public void amendUpQuantity() {
		final NewRequest newRequest1 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		final NewRequest newRequest2 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(newRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			assertThat("ask market depth after new orders", resp2.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", resp2.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(newRequest1.getPrice()).quantity(newRequest1.getQuantity() + newRequest2.getQuantity()).build()));
		}

		final AmendRequest amendOrder1 = AmendRequest.builder().orderId(newRequest1.getOrderId()).side(newRequest1.getSide()).orderType(newRequest1.getOrderType()).newOrderQuantity(123).newPrice(newRequest1.getPrice()).build();
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));
			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(newRequest1.getPrice()).quantity(amendOrder1.getNewOrderQuantity() + newRequest2.getQuantity()).build()));
		}
	}

	@Test
	public void amendUpQuantityChangesExecutionOrder() {
		final NewRequest buyRequest1 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(110).price(99.9).build();
		final NewRequest buyRequest2 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		{
			final Response resp1 = this.orderBook.submitRequest(buyRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(buyRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));

			assertThat("bid market depth after new orders", resp2.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after new orders", resp2.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new orders", resp2.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(buyRequest1.getPrice()).quantity(110 + 100).build()));
		}

		// originally, order 1 gets crossed first
		final NewRequest sellReq0 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.SELL).orderType(OrderType.LIMIT).quantity(10).price(99.9).build();
		{
			final Response sellResp0 = this.orderBook.submitRequest(sellReq0);
			assertThat("new sell order 0 successful", sellResp0, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 0", sellResp0.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(99.9).quantity((110 - 10) + 100).build()));
			assertThat("ask summary after sell 1", sellResp0.getAskSummary().getDepths(), empty());
			final SuccessResponse success0 = (SuccessResponse) sellResp0;
			assertThat("execution 1", success0.getExecutions(), containsInAnyOrder(
					Execution.builder().buyOrderId(buyRequest1.getOrderId()).sellOrderId(sellReq0.getOrderId()).quantity(10).price(sellReq0.getPrice()).build()
			));
		}
		final AmendRequest amendOrder1 = AmendRequest.builder().orderId(buyRequest1.getOrderId()).side(buyRequest1.getSide()).orderType(buyRequest1.getOrderType()).newOrderQuantity(110 - 10 + 23).newPrice(buyRequest1.getPrice()).build();
		{
			final Response amendResp1 = this.orderBook.submitRequest(amendOrder1);
			assertThat("amend action successful", amendResp1, instanceOf(SuccessResponse.class));

			assertThat("bid market depth after successful amend", amendResp1.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after successful amend", amendResp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after successful amend", amendResp1.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(buyRequest1.getPrice()).quantity(100 + 123).build()));
		}
		// cross now, order 2 should get executed before order 1
		final NewRequest sellReq1 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.SELL).orderType(OrderType.LIMIT).quantity(80).price(99.9).build();
		{
			final Response sellResp1 = this.orderBook.submitRequest(sellReq1);
			assertThat("new sell order 1 successful", sellResp1, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 1", sellResp1.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(99.9).quantity((100 - 80) + 123).build()));
			assertThat("ask summary after sell 1", sellResp1.getAskSummary().getDepths(), empty());
			final SuccessResponse success1 = (SuccessResponse) sellResp1;
			assertThat("execution 1", success1.getExecutions(), containsInAnyOrder(
					Execution.builder().buyOrderId(buyRequest2.getOrderId()).sellOrderId(sellReq1.getOrderId()).quantity(80).price(sellReq1.getPrice()).build()
			));
		}
		final NewRequest sellReq2 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.SELL).orderType(OrderType.LIMIT).quantity(45).price(99.9).build();
		{
			final Response sellResp2 = this.orderBook.submitRequest(sellReq2);
			assertThat("new sell order 2 successful", sellResp2, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 1", sellResp2.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(99.9).quantity((100 - 80 - 20) + (123 - 25)).build()));
			assertThat("ask summary after sell 1", sellResp2.getAskSummary().getDepths(), empty());
			final SuccessResponse success2 = (SuccessResponse) sellResp2;
			// finish crossing with order 2 first, then order 1
			assertThat("execution 2", success2.getExecutions(), containsInAnyOrder(
					Execution.builder().buyOrderId(buyRequest2.getOrderId()).sellOrderId(sellReq2.getOrderId()).quantity(20).price(sellReq2.getPrice()).build(),
					Execution.builder().buyOrderId(buyRequest1.getOrderId()).sellOrderId(sellReq2.getOrderId()).quantity(25).price(sellReq2.getPrice()).build()
			));
		}
	}

	@Test
	public void amendPriceCanTriggerExecution() {
		final NewRequest buyRequest1 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(100).price(99.9).build();
		final NewRequest buyRequest2 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.BUY).orderType(OrderType.LIMIT).quantity(200).price(99.9).build();
		final NewRequest sellRequest3 = NewRequest.builder().orderId(this.idGenerator.getNextId()).side(Side.SELL).orderType(OrderType.LIMIT).quantity(140).price(100.5).build();
		{
			final Response resp1 = this.orderBook.submitRequest(buyRequest1);
			assertThat("new order 1 action successful", resp1, instanceOf(SuccessResponse.class));
			final Response resp2 = this.orderBook.submitRequest(buyRequest2);
			assertThat("new order 2 action successful", resp2, instanceOf(SuccessResponse.class));
			final Response resp3 = this.orderBook.submitRequest(sellRequest3);
			assertThat("new order 3 action successful", resp3, instanceOf(SuccessResponse.class));

			final SuccessResponse resp = (SuccessResponse) resp3;
			assertThat("bid summary after new orders", resp.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(buyRequest1.getPrice()).quantity(100 + 200).build()));
			assertThat("ask summary after new orders", resp.getAskSummary().getDepths(), contains(
					PriceQuantity.builder().price(sellRequest3.getPrice()).quantity(140).build()));
			assertThat("executions after new order", resp.getExecutions(), empty());
		}
		// just amend price
		final AmendRequest amendReq4 = AmendRequest.builder().orderId(buyRequest2.getOrderId()).side(buyRequest2.getSide()).orderType(buyRequest2.getOrderType()).newOrderQuantity(buyRequest2.getQuantity()).newPrice(sellRequest3.getPrice()).build();
		{
			final Response resp4 = this.orderBook.submitRequest(amendReq4);
			assertThat("amend action successful", resp4, instanceOf(SuccessResponse.class));
			final SuccessResponse resp = (SuccessResponse) resp4;
			assertThat("bid summary after amend price", resp.getBidSummary().getDepths(), contains(
					PriceQuantity.builder().price(amendReq4.getNewPrice()).quantity(200 - 140).build(),
					PriceQuantity.builder().price(buyRequest1.getPrice()).quantity(100).build()
			));
			assertThat("ask summary after amend price", resp.getAskSummary().getDepths(), empty());

			assertThat("executions after amend price", resp.getExecutions(), containsInAnyOrder(
					Execution.builder().buyOrderId(buyRequest2.getOrderId()).sellOrderId(sellRequest3.getOrderId()).quantity(140).price(sellRequest3.getPrice()).build()
			));
		}
	}
}
