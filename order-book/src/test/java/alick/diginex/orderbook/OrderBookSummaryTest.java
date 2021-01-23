package alick.diginex.orderbook;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.AmendRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.request.Request;
import alick.diginex.orderbook.response.*;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderBookSummaryTest extends OrderBookTestBase {
	@Test
	public void unknownRequestTypeShouldThrow() {
		final Request unknownType = new Request(10) {
		};

		assertThrows(
				UnsupportedOperationException.class,
				() -> this.orderBook.submitRequest(unknownType),
				"unknown request type should throw"
		);
	}

	@Test
	public void verifyOrderBookSummaryMovement() {
		final NewRequest buy1 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1000, 99.9);
		final NewRequest buy2 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 2000, 99.8);
		final NewRequest buy3 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 3000, 99.7);
		final NewRequest sell1 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 1000, 100.1);
		final NewRequest sell2 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 2000, 100.2);
		final NewRequest sell3 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 100.3);
		{
			final Response buyResp1 = this.orderBook.submitRequest(buy1);
			assertThat("buy order 1", buyResp1, instanceOf(SuccessResponse.class));
			assertThat("buy order 1 response has no execution", buyResp1.getExecutions(), empty());
			final Response sellResp1 = this.orderBook.submitRequest(sell1);
			assertThat("sell order 1", sellResp1, instanceOf(SuccessResponse.class));
			assertThat("sell order 1 response has no execution", sellResp1.getExecutions(), empty());
			final Response buyResp2 = this.orderBook.submitRequest(buy2);
			assertThat("buy order 2", buyResp2, instanceOf(SuccessResponse.class));
			assertThat("buy order 2 response has no execution", buyResp2.getExecutions(), empty());
			final Response sellResp2 = this.orderBook.submitRequest(sell2);
			assertThat("sell order 2", sellResp2, instanceOf(SuccessResponse.class));
			assertThat("sell order 2 response has no execution", sellResp2.getExecutions(), empty());
			final Response buyResp3 = this.orderBook.submitRequest(buy3);
			assertThat("buy order 3", buyResp3, instanceOf(SuccessResponse.class));
			assertThat("buy order 3 response has no execution", buyResp3.getExecutions(), empty());
			final Response sellResp3 = this.orderBook.submitRequest(sell3);
			assertThat("sell order 3", sellResp3, instanceOf(SuccessResponse.class));
			assertThat("sell order 3 response has no execution", sellResp3.getExecutions(), empty());

			final SuccessResponse resp = (SuccessResponse) sellResp3;
			assertThat("bid summary after initial setup", sellResp3.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.9, 1000),
					new Level2Summary.PriceQuantity(99.8, 2000),
					new Level2Summary.PriceQuantity(99.7, 3000)));
			assertThat("ask summary after initial setup", sellResp3.getAskSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(100.1, 1000),
					new Level2Summary.PriceQuantity(100.2, 2000),
					new Level2Summary.PriceQuantity(100.3, 3000)));
			assertThat("execution after initial setup", resp.getExecutions(), empty());
			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("bid market order queue", snapshot.getBidMarketQueue(), hasSize(0));
			assertThat("ask market order queue", snapshot.getAskMarketQueue(), hasSize(0));
			assertThat("bid limit order queue", snapshot.getBidLimitQueue(), aMapWithSize(3));
			assertThat("ask limit order queue", snapshot.getAskLimitQueue(), aMapWithSize(3));
		}

		final NewRequest buy1001 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1500, 100.1);
		{
			final Response buy1001resp = this.orderBook.submitRequest(buy1001);
			assertThat("buy order 1500@$100.1", buy1001resp, instanceOf(SuccessResponse.class));

			assertThat("bid summary after buy 1500@$100.1", buy1001resp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(100.1, 1500 - 1000),
					new Level2Summary.PriceQuantity(99.9, 1000),
					new Level2Summary.PriceQuantity(99.8, 2000),
					new Level2Summary.PriceQuantity(99.7, 3000)));
			assertThat("ask summary after 1500@$100.1", buy1001resp.getAskSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(100.2, 2000),
					new Level2Summary.PriceQuantity(100.3, 3000)));
			assertThat("execution from buy 1500$100.1", buy1001resp.getExecutions(), containsInAnyOrder(
					new Execution(buy1001.getOrderId(), sell1.getOrderId(), 1000, 100.1)
			));
		}

		final AmendRequest amend1001 = new AmendRequest(buy1001.getOrderId(), buy1001.getSide(), buy1001.getOrderType(), 1600, 100.0);
		{
			final Response amend1001Resp = this.orderBook.submitRequest(amend1001);
			assertThat("amend buy order 500@$100.1 -> 1600@$100", amend1001Resp, instanceOf(SuccessResponse.class));

			assertThat("bid summary after amend buy 1500@$100.1 -> 1600@$100", amend1001Resp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(100.0, 1600),
					new Level2Summary.PriceQuantity(99.9, 1000),
					new Level2Summary.PriceQuantity(99.8, 2000),
					new Level2Summary.PriceQuantity(99.7, 3000)));
			assertThat("ask summary after amend buy 1500@$100.1 -> 1600@$100", amend1001Resp.getAskSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(100.2, 2000),
					new Level2Summary.PriceQuantity(100.3, 3000)));
			assertThat("execution from amend buy 1500@$100.1 -> 1600@$100", amend1001Resp.getExecutions(), empty());
		}

		final NewRequest sell999 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 99.9);
		{
			final Response sell999Resp = this.orderBook.submitRequest(sell999);
			assertThat("sell 3000@$99.9", sell999Resp, instanceOf(SuccessResponse.class));

			assertThat("bid summary after sell 3000@$99.9", sell999Resp.getBidSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.8, 2000),
					new Level2Summary.PriceQuantity(99.7, 3000)));
			assertThat("ask summary after sell 3000@$99.9", sell999Resp.getAskSummary().getDepths(), contains(
					new Level2Summary.PriceQuantity(99.9, 3000 - 1600 - 1000),
					new Level2Summary.PriceQuantity(100.1, 0),
					new Level2Summary.PriceQuantity(100.2, 2000),
					new Level2Summary.PriceQuantity(100.3, 3000)));
			assertThat("execution from sell sell 3000@$99.9", sell999Resp.getExecutions(), containsInAnyOrder(
					new Execution(buy1.getOrderId(), sell999.getOrderId(), 1000, 99.9),
					new Execution(buy1001.getOrderId(), sell999.getOrderId(), 1600, 100.0)
			));
		}
	}

	@Test
	public void summaryResponseShouldContainAllExecutions() {
		// same test as verifyOrderBookSummaryMovement, but executed all together
		final NewRequest buy1 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1000, 99.9);
		final NewRequest buy2 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 2000, 99.8);
		final NewRequest buy3 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 3000, 99.7);
		final NewRequest sell1 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 1000, 100.1);
		final NewRequest sell2 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 2000, 100.2);
		final NewRequest sell3 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 100.3);
		final NewRequest buy1001 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1500, 100.1);
		final AmendRequest amend1001 = new AmendRequest(buy1001.getOrderId(), buy1001.getSide(), buy1001.getOrderType(), 1600, 100.0);
		final NewRequest sell999 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 99.9);

		final Response buy1Resp = this.orderBook.submitRequest(buy1);
		assertThat("buy order 1 response", buy1Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 1 response has no execution", buy1Resp.getExecutions(), empty());
		final Response buy2Resp = this.orderBook.submitRequest(buy2);
		assertThat("buy order 2 response", buy2Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 2 response has no execution", buy2Resp.getExecutions(), empty());
		final Response buy3Resp = this.orderBook.submitRequest(buy3);
		assertThat("buy order 3 response", buy3Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 3 response has no execution", buy3Resp.getExecutions(), empty());
		final Response sell1Resp = this.orderBook.submitRequest(sell1);
		assertThat("sell order 1 response", sell1Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 1 response has no execution", sell1Resp.getExecutions(), empty());
		final Response sell2Resp = this.orderBook.submitRequest(sell2);
		assertThat("sell order 2 response", sell2Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 2 response has no execution", sell2Resp.getExecutions(), empty());
		final Response sell3Resp = this.orderBook.submitRequest(sell3);
		assertThat("sell order 3 response", sell3Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 3 response has no execution", sell3Resp.getExecutions(), empty());
		final Response buy1001Resp = this.orderBook.submitRequest(buy1001);
		assertThat("buy 100.1 response", buy1001Resp, instanceOf(SuccessResponse.class));
		assertThat("execution after new buy @100.1", buy1001Resp.getExecutions(), contains(new Execution(buy1001.getOrderId(), sell1.getOrderId(), 1000, 100.1)));
		final Response amend1001Resp = this.orderBook.submitRequest(amend1001);
		assertThat("amend 100.1 response", amend1001Resp, instanceOf(SuccessResponse.class));
		assertThat("amend 100.1 response has no execution", amend1001Resp.getExecutions(), empty());
		final Response sell999Resp = this.orderBook.submitRequest(sell999);
		assertThat("sell 99.9 response", sell999Resp, instanceOf(SuccessResponse.class));
		final SuccessResponse resp = (SuccessResponse) sell999Resp;

		assertThat("bid summary after combined execution", resp.getBidSummary().getDepths(), contains(
				new Level2Summary.PriceQuantity(99.8, 2000),
				new Level2Summary.PriceQuantity(99.7, 3000)));
		assertThat("ask summary after combined execution", resp.getAskSummary().getDepths(), contains(
				new Level2Summary.PriceQuantity(99.9, 3000 - 1600 - 1000),
				new Level2Summary.PriceQuantity(100.1, 0),
				new Level2Summary.PriceQuantity(100.2, 2000),
				new Level2Summary.PriceQuantity(100.3, 3000)));

		// execution order is actually important!
		assertThat("sell 99.9 response has execution", resp.getExecutions(), contains(
				new Execution(buy1001.getOrderId(), sell999.getOrderId(), 1600, 100),
				new Execution(buy1.getOrderId(), sell999.getOrderId(), 1000, 99.9)));
	}

	@Test
	public void batchedExecutionShouldHaltAtBadRequest() {
		// same sequence of requests as summary response test, except that the amend request is bad, so execution summary
		// should contain up to the  buy order 1500 shr @ 100.1
		final NewRequest buy1 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1000, 99.9);
		final NewRequest buy2 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 2000, 99.8);
		final NewRequest buy3 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 3000, 99.7);
		final NewRequest sell1 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 1000, 100.1);
		final NewRequest sell2 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 2000, 100.2);
		final NewRequest sell3 = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 100.3);
		final NewRequest buy1001 = new NewRequest(idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 1500, 100.1);

		final AmendRequest badAmendRequest = new AmendRequest(buy1001.getOrderId() * -1, buy1001.getSide(), buy1001.getOrderType(), 1600, 100.0);
//		final NewRequest newOrderShouldNotExecute = new NewRequest(idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 3000, 99.9);

		final Response buy1Resp = this.orderBook.submitRequest(buy1);
		assertThat("buy order 1 response", buy1Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 1 response has no execution", buy1Resp.getExecutions(), empty());
		final Response buy2Resp = this.orderBook.submitRequest(buy2);
		assertThat("buy order 2 response", buy2Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 2 response has no execution", buy2Resp.getExecutions(), empty());
		final Response buy3Resp = this.orderBook.submitRequest(buy3);
		assertThat("buy order 3 response", buy3Resp, instanceOf(SuccessResponse.class));
		assertThat("buy order 3 response has no execution", buy3Resp.getExecutions(), empty());
		final Response sell1Resp = this.orderBook.submitRequest(sell1);
		assertThat("sell order 1 response", sell1Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 1 response has no execution", sell1Resp.getExecutions(), empty());
		final Response sell2Resp = this.orderBook.submitRequest(sell2);
		assertThat("sell order 2 response", sell2Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 2 response has no execution", sell2Resp.getExecutions(), empty());
		final Response sell3Resp = this.orderBook.submitRequest(sell3);
		assertThat("sell order 3 response", sell3Resp, instanceOf(SuccessResponse.class));
		assertThat("sell order 3 response has no execution", sell3Resp.getExecutions(), empty());
		final Response buy1001Resp = this.orderBook.submitRequest(buy1001);
		assertThat("buy 100.1 response", buy1001Resp, instanceOf(SuccessResponse.class));
		assertThat("execution from buy 100.1", buy1001Resp.getExecutions(), contains(new Execution(buy1001.getOrderId(), sell1.getOrderId(), 1000, 100.1)));

		final Response badAmendResp = this.orderBook.submitRequest(badAmendRequest);
		assertThat("fail amend response", badAmendResp, instanceOf(ErrorResponse.class));

		final ErrorResponse resp = (ErrorResponse) badAmendResp;
		assertThat("Bad response contains bad order id", resp.getOrderId(), equalTo(badAmendRequest.getOrderId()));
		assertThat("bid summary after buy 1500@$100.1", resp.getBidSummary().getDepths(), contains(
				new Level2Summary.PriceQuantity(100.1, 1500 - 1000),
				new Level2Summary.PriceQuantity(99.9, 1000),
				new Level2Summary.PriceQuantity(99.8, 2000),
				new Level2Summary.PriceQuantity(99.7, 3000)));
		assertThat("ask summary after 1500@$100.1", resp.getAskSummary().getDepths(), contains(
				new Level2Summary.PriceQuantity(100.2, 2000),
				new Level2Summary.PriceQuantity(100.3, 3000)));
		assertThat("execution from bad request", resp.getExecutions(), empty());
	}
}