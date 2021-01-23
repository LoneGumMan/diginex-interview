package alick.diginex.orderbook;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderBookSnapshot.OrderOpenQty;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.orderbook.request.CancelRequest;
import alick.diginex.orderbook.request.NewRequest;
import alick.diginex.orderbook.response.ErrorResponse;
import alick.diginex.orderbook.response.Execution;
import alick.diginex.orderbook.response.Level2Summary.PriceQuantity;
import alick.diginex.orderbook.response.Response;
import alick.diginex.orderbook.response.SuccessResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class OrderBookNewCancelTest extends OrderBookTestBase {
	@Test
	public void addFirstBuyOrderThenCancel() {
		final NewRequest newRequest = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest);
			assertThat("new order action successful", resp1, instanceOf(SuccessResponse.class));
			assertThat("bid market depth after new order", resp1.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after new order", resp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new order", resp1.getBidSummary().getDepths(), contains(
					new PriceQuantity(newRequest.getPrice(), newRequest.getQuantity())));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), aMapWithSize(1));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), anEmptyMap());

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(newRequest.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(newRequest.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), newRequest.getQuantity())));
		}
		final CancelRequest cancelRequest = new CancelRequest(newRequest.getOrderId());
		{
			final Response resp2 = this.orderBook.submitRequest(cancelRequest);
			assertThat("cancel successful", resp2, instanceOf(SuccessResponse.class));
			assertThat("bid market depth after cancel", resp2.getBidSummary().getDepths(), empty());
			assertThat("ask market depth after cancel", resp2.getAskSummary().getDepths(), empty());

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue after cancel", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue after cancel", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue after cancel", snapshot.getBidLimitQueue(), anEmptyMap());
			assertThat("snapshot ask limit-price queue after cancel", snapshot.getAskLimitQueue(), anEmptyMap());
		}
	}

	@Test
	public void addFirstSellOrderThenCancel() {
		final NewRequest newRequest = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 100, 100.1);
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest);
			assertThat("new order action successful", resp1, instanceOf(SuccessResponse.class));

			assertThat("bid market depth after new order", resp1.getBidSummary().getDepths(), empty());
			assertThat("ask market depth after new order", resp1.getAskSummary().getDepths(), hasSize(1));
			assertThat("ask summary after new order", resp1.getAskSummary().getDepths(), contains(
					new PriceQuantity(newRequest.getPrice(), newRequest.getQuantity())));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), anEmptyMap());
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), aMapWithSize(1));

			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(newRequest.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(newRequest.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), newRequest.getQuantity())));
		}
		final CancelRequest cancelRequest = new CancelRequest(newRequest.getOrderId());
		{
			final Response resp2 = this.orderBook.submitRequest(cancelRequest);
			assertThat("cancel successful", resp2, instanceOf(SuccessResponse.class));
			assertThat("bid market depth after cancel", resp2.getBidSummary().getDepths(), empty());
			assertThat("ask market depth after cancel", resp2.getAskSummary().getDepths(), empty());

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue after cancel", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue after cancel", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue after cancel", snapshot.getBidLimitQueue(), anEmptyMap());
			assertThat("snapshot ask limit-price queue after cancel", snapshot.getAskLimitQueue(), anEmptyMap());
		}
	}

	@Test
	public void addSeveralBuySellOrdersNoCrossing() {
		final NewRequest buy1_999 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		final NewRequest buy2_998 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 200, 99.8);
		final NewRequest buy3_997 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 300, 99.7);
		final NewRequest buy4_999 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 400, 99.9);
		final NewRequest sell1_1001 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 500, 100.1);
		final NewRequest sell2_1002 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 600, 100.2);
		final NewRequest sell3_1003 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 700, 100.3);
		final NewRequest sell4_1001 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 800, 100.1);

		final Response resp1;
		{
			resp1 = this.orderBook.submitRequest(buy1_999);
			assertThat("bid market depth after order 1", resp1.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after order 1", resp1.getAskSummary().getDepths(), empty());
			assertThat("bid summary after order 1", resp1.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100)));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), aMapWithSize(1));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), anEmptyMap());

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buy1_999.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy1_999.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity())));
		}
		final Response resp2;
		{
			resp2 = this.orderBook.submitRequest(sell1_1001);
			assertThat("bid market depth after order 2", resp2.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after order 2", resp2.getAskSummary().getDepths(), hasSize(1));
			assertThat("bid summary after order 2", resp2.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100)));
			assertThat("ask summary after order 2", resp2.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500)));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), aMapWithSize(1));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), aMapWithSize(1));

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buy1_999.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy1_999.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity())));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
					new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity())));
		}
		final Response resp3;
		{
			resp3 = this.orderBook.submitRequest(buy2_998);
			assertThat("bid market depth after order 3", resp3.getBidSummary().getDepths(), hasSize(2));
			assertThat("ask market depth after order 3", resp3.getAskSummary().getDepths(), hasSize(1));
			assertThat("bid summary after order 3", resp3.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100),
					new PriceQuantity(99.8, 200)));
			assertThat("ask summary after order 3", resp3.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500)));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), aMapWithSize(2));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), aMapWithSize(1));

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buy2_998.getPrice(), buy1_999.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy1_999.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity())));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy2_998.getPrice()), contains(
					new OrderOpenQty(resp3.getOrderId(), buy2_998.getQuantity())));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
					new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity())));
		}
		final Response resp4;
		{
			resp4 = this.orderBook.submitRequest(sell2_1002);
			assertThat("bid market depth after order 4", resp4.getBidSummary().getDepths(), hasSize(2));
			assertThat("ask market depth after order 4", resp4.getAskSummary().getDepths(), hasSize(2));
			assertThat("bid summary after order 4", resp4.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100),
					new PriceQuantity(99.8, 200)));
			assertThat("ask summary after order 4", resp4.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500),
					new PriceQuantity(100.2, 600)));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price queue", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price queue", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue(), aMapWithSize(2));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue(), aMapWithSize(2));

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buy2_998.getPrice(), buy1_999.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy1_999.getPrice()), contains(
					new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity())));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buy2_998.getPrice()), contains(
					new OrderOpenQty(resp3.getOrderId(), buy2_998.getQuantity())));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice(), sell2_1002.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
					new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity())));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sell2_1002.getPrice()), contains(
					new OrderOpenQty(resp4.getOrderId(), sell2_1002.getQuantity())));
		}
		final Response resp5;
		{
			resp5 = this.orderBook.submitRequest(buy3_997);
			assertThat("bid market depth after order 5", resp5.getBidSummary().getDepths(), hasSize(3));
			assertThat("ask market depth after order 5", resp5.getAskSummary().getDepths(), hasSize(2));
			assertThat("bid summary after order 5", resp5.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", resp5.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500),
					new PriceQuantity(100.2, 600)));
		}
		final Response resp6;
		{
			resp6 = this.orderBook.submitRequest(sell3_1003);
			assertThat("bid market depth after order 6", resp6.getBidSummary().getDepths(), hasSize(3));
			assertThat("ask market depth after order 6", resp6.getAskSummary().getDepths(), hasSize(3));
			assertThat("bid summary after order 6", resp6.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", resp6.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500),
					new PriceQuantity(100.2, 600),
					new PriceQuantity(100.3, 700)));
		}
		final Response resp7;
		{
			resp7 = this.orderBook.submitRequest(buy4_999);
			assertThat("bid market depth after order 7", resp7.getBidSummary().getDepths(), hasSize(3));
			assertThat("ask market depth after order 7", resp7.getAskSummary().getDepths(), hasSize(3));
			assertThat("bid summary after order 7", resp7.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 500),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", resp7.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 500),
					new PriceQuantity(100.2, 600),
					new PriceQuantity(100.3, 700)));
		}
		final Response resp8;
		{
			resp8 = this.orderBook.submitRequest(sell4_1001);
			assertThat("bid market depth after order 8", resp8.getBidSummary().getDepths(), hasSize(3));
			assertThat("ask market depth after order 8", resp8.getAskSummary().getDepths(), hasSize(3));
			assertThat("bid summary after order 8", resp8.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 500),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", resp8.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 1300),
					new PriceQuantity(100.2, 600),
					new PriceQuantity(100.3, 700)));
		}

		final OrderBookSnapshot snapshot1 = this.orderBook.snapshotOrderBook();
		assertThat("snapshot bid market-price queue all orders", snapshot1.getBidMarketQueue(), empty());
		assertThat("snapshot ask market-price queue all orders", snapshot1.getAskMarketQueue(), empty());
		assertThat("snapshot bid limit-price queue all orders", snapshot1.getBidLimitQueue(), aMapWithSize(3));
		assertThat("snapshot ask limit-price queue all orders", snapshot1.getAskLimitQueue(), aMapWithSize(3));

		assertThat("snapshot bid limit-price queue all orders", snapshot1.getBidLimitQueue().keySet(), containsInAnyOrder(buy2_998.getPrice(), buy1_999.getPrice(), buy3_997.getPrice()));
		assertThat("snapshot bid limit-price queue all orders", snapshot1.getBidLimitQueue().get(buy1_999.getPrice()), contains(
				new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity()),
				new OrderOpenQty(resp7.getOrderId(), buy4_999.getQuantity())));
		assertThat("snapshot bid limit-price queue all orders", snapshot1.getBidLimitQueue().get(buy2_998.getPrice()), contains(
				new OrderOpenQty(resp3.getOrderId(), buy2_998.getQuantity())));
		assertThat("snapshot bid limit-price queue all orders", snapshot1.getBidLimitQueue().get(buy3_997.getPrice()), contains(
				new OrderOpenQty(resp5.getOrderId(), buy3_997.getQuantity())));

		assertThat("snapshot ask limit-price queue all orders", snapshot1.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice(), sell2_1002.getPrice(), sell3_1003.getPrice()));
		assertThat("snapshot ask limit-price queue all orders", snapshot1.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
				new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity()),
				new OrderOpenQty(resp8.getOrderId(), sell4_1001.getQuantity())));
		assertThat("snapshot ask limit-price queue all orders", snapshot1.getAskLimitQueue().get(sell2_1002.getPrice()), contains(
				new OrderOpenQty(resp4.getOrderId(), sell2_1002.getQuantity())));
		assertThat("snapshot ask limit-price queue all orders", snapshot1.getAskLimitQueue().get(sell3_1003.getPrice()), contains(
				new OrderOpenQty(resp6.getOrderId(), sell3_1003.getQuantity())));

		{
			final CancelRequest cancelSellReq = new CancelRequest(sell2_1002.getOrderId());
			final Response cxlSellResp = this.orderBook.submitRequest(cancelSellReq);
			assertThat("cancel sell request", cxlSellResp, instanceOf(SuccessResponse.class));
			assertThat("bid summary after order 8", cxlSellResp.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 500),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", cxlSellResp.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 1300),
					new PriceQuantity(100.2, 0),
					new PriceQuantity(100.3, 700)));
		}
		final OrderBookSnapshot snapshot2 = this.orderBook.snapshotOrderBook();
		assertThat("snapshot bid market-price queue cancel sell req 2", snapshot2.getBidMarketQueue(), empty());
		assertThat("snapshot ask market-price queue cancel sell req 2", snapshot2.getAskMarketQueue(), empty());
		assertThat("snapshot bid limit-price queue cancel sell req 2", snapshot2.getBidLimitQueue(), aMapWithSize(3));
		assertThat("snapshot ask limit-price queue cancel sell req 2", snapshot2.getAskLimitQueue(), aMapWithSize(3));

		assertThat("snapshot bid limit-price queue cancel sell req 2", snapshot2.getBidLimitQueue().keySet(), containsInAnyOrder(buy2_998.getPrice(), buy1_999.getPrice(), buy3_997.getPrice()));
		assertThat("snapshot bid limit-price queue cancel sell req 2", snapshot2.getBidLimitQueue().get(buy1_999.getPrice()), contains(
				new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity()),
				new OrderOpenQty(resp7.getOrderId(), buy4_999.getQuantity())));
		assertThat("snapshot bid limit-price queue cancel sell req 2", snapshot2.getBidLimitQueue().get(buy2_998.getPrice()), contains(
				new OrderOpenQty(resp3.getOrderId(), buy2_998.getQuantity())));
		assertThat("snapshot bid limit-price queue cancel sell req 2", snapshot2.getBidLimitQueue().get(buy3_997.getPrice()), contains(
				new OrderOpenQty(resp5.getOrderId(), buy3_997.getQuantity())));

		assertThat("snapshot ask limit-price queue cancel sell req 2", snapshot2.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice(), sell2_1002.getPrice(), sell3_1003.getPrice()));
		assertThat("snapshot ask limit-price queue cancel sell req 2", snapshot2.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
				new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity()),
				new OrderOpenQty(resp8.getOrderId(), sell4_1001.getQuantity())));
		assertThat("snapshot ask limit-price queue cancel sell req 2", snapshot2.getAskLimitQueue().get(sell2_1002.getPrice()), empty());
		assertThat("snapshot ask limit-price queue cancel sell req 2", snapshot2.getAskLimitQueue().get(sell3_1003.getPrice()), contains(
				new OrderOpenQty(resp6.getOrderId(), sell3_1003.getQuantity())));
		{
			final CancelRequest cancelBuyReq = new CancelRequest(buy2_998.getOrderId());
			final Response cxlBuyResp = this.orderBook.submitRequest(cancelBuyReq);
			assertThat("cancel buy request", cxlBuyResp, instanceOf(SuccessResponse.class));
			assertThat("bid summary after order 8", cxlBuyResp.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 500),
					new PriceQuantity(99.8, 0),
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after order 5", cxlBuyResp.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 1300),
					new PriceQuantity(100.2, 0),
					new PriceQuantity(100.3, 700)));
		}
		final OrderBookSnapshot snapshot3 = this.orderBook.snapshotOrderBook();
		assertThat("snapshot bid market-price queue cancel buy req 2", snapshot3.getBidMarketQueue(), empty());
		assertThat("snapshot ask market-price queue cancel buy req 2", snapshot3.getAskMarketQueue(), empty());
		assertThat("snapshot bid limit-price queue cancel buy req 2", snapshot3.getBidLimitQueue(), aMapWithSize(3));
		assertThat("snapshot ask limit-price queue cancel buy req 2", snapshot3.getAskLimitQueue(), aMapWithSize(3));

		assertThat("snapshot bid limit-price queue cancel buy req 2", snapshot3.getBidLimitQueue().keySet(), containsInAnyOrder(buy2_998.getPrice(), buy1_999.getPrice(), buy3_997.getPrice()));
		assertThat("snapshot bid limit-price queue cancel buy req 2", snapshot3.getBidLimitQueue().get(buy1_999.getPrice()), contains(
				new OrderOpenQty(resp1.getOrderId(), buy1_999.getQuantity()),
				new OrderOpenQty(resp7.getOrderId(), buy4_999.getQuantity())));
		assertThat("snapshot bid limit-price queue cancel buy req 2", snapshot3.getBidLimitQueue().get(buy2_998.getPrice()), empty());
		assertThat("snapshot bid limit-price queue cancel buy req 2", snapshot3.getBidLimitQueue().get(buy3_997.getPrice()), contains(
				new OrderOpenQty(resp5.getOrderId(), buy3_997.getQuantity())));

		assertThat("snapshot ask limit-price queue cancel buy req 2", snapshot3.getAskLimitQueue().keySet(), containsInAnyOrder(sell1_1001.getPrice(), sell2_1002.getPrice(), sell3_1003.getPrice()));
		assertThat("snapshot ask limit-price queue cancel buy req 2", snapshot3.getAskLimitQueue().get(sell1_1001.getPrice()), contains(
				new OrderOpenQty(resp2.getOrderId(), sell1_1001.getQuantity()),
				new OrderOpenQty(resp8.getOrderId(), sell4_1001.getQuantity())));
		assertThat("snapshot ask limit-price queue cancel buy req 2", snapshot3.getAskLimitQueue().get(sell2_1002.getPrice()), empty());
		assertThat("snapshot ask limit-price queue cancel buy req 2", snapshot3.getAskLimitQueue().get(sell3_1003.getPrice()), contains(
				new OrderOpenQty(resp6.getOrderId(), sell3_1003.getQuantity())));
	}

	@Test
	public void enterCrossableLimitOrder() {
		final NewRequest buyReq1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 400, 99.9);
		final NewRequest sellReq1 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 500, 100.1);
		final NewRequest sellReq2 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 50, 100.1);
		final Response buyResp1, sellResp1, sellResp2;
		{
			buyResp1 = this.orderBook.submitRequest(buyReq1);
			assertThat("buy order 1 actions successful", buyResp1, instanceOf(SuccessResponse.class));
			assertThat("buy order 1 actions no execution", buyResp1.getExecutions(), empty());
			sellResp1 = this.orderBook.submitRequest(sellReq1);
			assertThat("sell order 1 actions successful", sellResp1, instanceOf(SuccessResponse.class));
			assertThat("sell order 1 actions no execution", sellResp1.getExecutions(), empty());
			sellResp2 = this.orderBook.submitRequest(sellReq2);
			assertThat("sell order 2 actions successful", sellResp2, instanceOf(SuccessResponse.class));
			assertThat("sell order 2 actions no execution", sellResp2.getExecutions(), empty());

			assertThat("bid summary after initial orders", sellResp2.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 400)));
			assertThat("ask summary after initial orders", sellResp2.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 550)));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price", snapshot.getBidLimitQueue(), aMapWithSize(1));
			assertThat("snapshot ask limit-price", snapshot.getAskLimitQueue(), aMapWithSize(1));

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buyReq1.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buyReq1.getPrice()), contains(
					new OrderOpenQty(buyResp1.getOrderId(), buyReq1.getQuantity())));

			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(sellReq1.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sellReq1.getPrice()), contains(
					new OrderOpenQty(sellResp1.getOrderId(), sellReq1.getQuantity()),
					new OrderOpenQty(sellResp2.getOrderId(), sellReq2.getQuantity())));
		}

		final NewRequest sellExecReq = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 240, 99.9);
		final Response sellExecResp;
		{
			sellExecResp = this.orderBook.submitRequest(sellExecReq);
			assertThat("crossable sell order successful", sellExecResp, instanceOf(SuccessResponse.class));
			assertThat("bid summary after 1 sell order", sellExecResp.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 400 - 240)));
			assertThat("ask summary after 1 sell order", sellExecResp.getAskSummary().getDepths(), contains(
					new PriceQuantity(100.1, 550)));

			final SuccessResponse success1 = (SuccessResponse) sellExecResp;
			assertThat("execution", success1.getExecutions(), containsInAnyOrder(
					new Execution(buyReq1.getOrderId(), sellExecReq.getOrderId(), sellExecReq.getQuantity(), sellExecReq.getPrice())
			));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price", snapshot.getBidLimitQueue(), aMapWithSize(1));
			assertThat("snapshot ask limit-price", snapshot.getAskLimitQueue(), aMapWithSize(1));

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buyReq1.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buyReq1.getPrice()), contains(
					new OrderOpenQty(buyResp1.getOrderId(), buyReq1.getQuantity() - 240)));

			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().keySet(), containsInAnyOrder(sellReq1.getPrice()));
			assertThat("snapshot ask limit-price queue", snapshot.getAskLimitQueue().get(sellReq1.getPrice()), contains(
					new OrderOpenQty(sellResp1.getOrderId(), sellReq1.getQuantity()),
					new OrderOpenQty(sellResp2.getOrderId(), sellReq2.getQuantity())));
		}
		final NewRequest buyExecReq = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 580, 100.1);
		final Response buyExecResp;
		{
			buyExecResp = this.orderBook.submitRequest(buyExecReq);
			assertThat("crossable buy order successful", buyExecResp, instanceOf(SuccessResponse.class));

			assertThat("bid summary after 1 buy order partially filled", buyExecResp.getBidSummary().getDepths(), contains(
					new PriceQuantity(100.1, 580 - 550),
					new PriceQuantity(99.9, 400 - 240)));
			assertThat("ask summary after buy order consumed all queued sell order", buyExecResp.getAskSummary().getDepths(), empty());

			final SuccessResponse success2 = (SuccessResponse) buyExecResp;
			assertThat("execution", success2.getExecutions(), containsInAnyOrder(
					new Execution(buyExecReq.getOrderId(), sellReq1.getOrderId(), sellReq1.getQuantity(), buyExecReq.getPrice()),
					new Execution(buyExecReq.getOrderId(), sellReq2.getOrderId(), sellReq2.getQuantity(), buyExecReq.getPrice())
			));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price", snapshot.getBidLimitQueue(), aMapWithSize(2));
			assertThat("snapshot ask limit-price", snapshot.getAskLimitQueue(), anEmptyMap());

			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buyReq1.getPrice(), buyExecReq.getPrice()));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buyExecReq.getPrice()), contains(
					new OrderOpenQty(buyExecResp.getOrderId(), buyExecReq.getQuantity() - sellReq1.getQuantity() - sellReq2.getQuantity())));
			assertThat("snapshot bid limit-price queue", snapshot.getBidLimitQueue().get(buyReq1.getPrice()), contains(
					new OrderOpenQty(buyResp1.getOrderId(), buyReq1.getQuantity() - 240)));
		}
	}

	@Test
	public void executionAcrossMultipleOrdersObeyPriceTimePriority() {
		final NewRequest buyReq1 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 60, 99.9);
		final NewRequest buyReq2 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 200, 99.8);
		final NewRequest buyReq3 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 300, 99.7);
		// same price as buy-1, but later
		final NewRequest buyReq4 = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 40, 99.9);
		{
			final Response buyResp1 = this.orderBook.submitRequest(buyReq1);
			assertThat("new buy order 1 successful", buyResp1, instanceOf(SuccessResponse.class));
			assertThat("new byu order 1 no execution", buyResp1.getExecutions(), empty());
			final Response buyResp2 = this.orderBook.submitRequest(buyReq2);
			assertThat("new buy order 2 successful", buyResp2, instanceOf(SuccessResponse.class));
			assertThat("new byu order 2 no execution", buyResp2.getExecutions(), empty());
			final Response buyResp3 = this.orderBook.submitRequest(buyReq3);
			assertThat("new buy order 3 successful", buyResp3, instanceOf(SuccessResponse.class));
			assertThat("new byu order 3 no execution", buyResp3.getExecutions(), empty());
			final Response buyResp4 = this.orderBook.submitRequest(buyReq4);
			assertThat("new buy order 4 successful", buyResp4, instanceOf(SuccessResponse.class));
			assertThat("new byu order 4 no execution", buyResp4.getExecutions(), empty());
		}
		final NewRequest sellReq1 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.MARKET, 80, 0);
		{
			final Response sellResp1 = this.orderBook.submitRequest(sellReq1);
			assertThat("new sell order 1 successful", sellResp1, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 1", sellResp1.getBidSummary().getDepths(), contains(
					new PriceQuantity(99.9, 100 - 80),
					new PriceQuantity(99.8, 200),
					new PriceQuantity(99.7, 300)));

			assertThat("ask summary after sell 1", sellResp1.getAskSummary().getDepths(), empty());
			final SuccessResponse success1 = (SuccessResponse) sellResp1;
			assertThat("execution 1", success1.getExecutions(), contains(
					// buy 1 gets more qty
					new Execution(buyReq1.getOrderId(), sellReq1.getOrderId(), 60, buyReq1.getPrice()),
					// buy 2 gets residuals
					new Execution(buyReq4.getOrderId(), sellReq1.getOrderId(), 20, buyReq4.getPrice())
			));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price", snapshot.getBidLimitQueue(), aMapWithSize(3));
			assertThat("snapshot ask limit-price", snapshot.getAskLimitQueue(), anEmptyMap());
			assertThat("snapshot bid limit-price queue after sell market px", snapshot.getBidLimitQueue().get(buyReq4.getPrice()), contains(
					new OrderOpenQty(buyReq4.getOrderId(), buyReq4.getQuantity() - 20)
			));
			assertThat("snapshot bid limit-price queue after sell market px", snapshot.getBidLimitQueue().get(buyReq2.getPrice()), contains(
					new OrderOpenQty(buyReq2.getOrderId(), buyReq2.getQuantity())
			));
			assertThat("snapshot bid limit-price queue after sell market px", snapshot.getBidLimitQueue().get(buyReq3.getPrice()), contains(
					new OrderOpenQty(buyReq3.getOrderId(), buyReq3.getQuantity())
			));
		}
		final NewRequest sellReq2 = new NewRequest(this.idGenerator.getNextId(), Side.SELL, OrderType.LIMIT, 80, 99.7);
		{
			final Response sellResp2 = this.orderBook.submitRequest(sellReq2);
			assertThat("new sell order 2 successful", sellResp2, instanceOf(SuccessResponse.class));
			assertThat("bid summary after sell 2", sellResp2.getBidSummary().getDepths(), contains(
					//new PriceQuantity(99.9, 100-80), // 20 shr @ 99.9
					new PriceQuantity(99.8, 200 - 60), // 60 shr @ 99.8
					new PriceQuantity(99.7, 300)));
			assertThat("ask summary after sell 1", sellResp2.getAskSummary().getDepths(), empty());
			final SuccessResponse success2 = (SuccessResponse) sellResp2;
			assertThat("execution 1", success2.getExecutions(), containsInAnyOrder(
					new Execution(buyReq4.getOrderId(), sellReq2.getOrderId(), 20, buyReq1.getPrice()),
					new Execution(buyReq2.getOrderId(), sellReq2.getOrderId(), 60, buyReq2.getPrice())
			));

			final OrderBookSnapshot snapshot = this.orderBook.snapshotOrderBook();
			assertThat("snapshot bid market-price", snapshot.getBidMarketQueue(), empty());
			assertThat("snapshot ask market-price", snapshot.getAskMarketQueue(), empty());
			assertThat("snapshot bid limit-price", snapshot.getBidLimitQueue(), aMapWithSize(2));
			assertThat("snapshot ask limit-price", snapshot.getAskLimitQueue(), anEmptyMap());
			assertThat("snapshot bid limit-price queue after sell @99.7", snapshot.getBidLimitQueue().keySet(), containsInAnyOrder(buyReq2.getPrice(), buyReq3.getPrice()));
			assertThat("snapshot bid limit-price queue after sell @99.7", snapshot.getBidLimitQueue().get(buyReq2.getPrice()), contains(
					new OrderOpenQty(buyReq2.getOrderId(), buyReq2.getQuantity() - (80-60))
			));
			assertThat("snapshot bid limit-price queue after sell @99.7", snapshot.getBidLimitQueue().get(buyReq3.getPrice()), contains(
					new OrderOpenQty(buyReq3.getOrderId(), buyReq3.getQuantity())
			));
		}
	}

	@Test
	public void cancelNonExistentOrder() {
		final NewRequest newRequest = new NewRequest(this.idGenerator.getNextId(), Side.BUY, OrderType.LIMIT, 100, 99.9);
		{
			final Response resp1 = this.orderBook.submitRequest(newRequest);
			assertThat("new order action successful", resp1, instanceOf(SuccessResponse.class));
		}
		final long orderId = idGenerator.getNextId();
		final CancelRequest request = new CancelRequest(orderId);
		{
			final Response response = this.orderBook.submitRequest(request);
			assertThat("expects error for cancelling non-existent order", response, instanceOf(ErrorResponse.class));
			assertThat("order ID should be same as passed in", response.getOrderId(), equalTo(orderId));
			assertThat("bid market depth after new order", response.getBidSummary().getDepths(), hasSize(1));
			assertThat("ask market depth after new order", response.getAskSummary().getDepths(), empty());
			assertThat("bid summary after new order", response.getBidSummary().getDepths(), contains(
					new PriceQuantity(newRequest.getPrice(), newRequest.getQuantity())
			));
		}
	}
}
