package alick.diginex.matchingengine;

import alick.diginex.entities.OrderBookSnapshot;
import alick.diginex.entities.OrderBookSnapshot.OrderOpenQty;
import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.matchingengine.entities.OrderStatus;
import alick.diginex.matchingengine.message.ExecutionReport;
import alick.diginex.matchingengine.message.ResponseMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Random;

import static alick.diginex.entities.OrderType.LIMIT;
import static alick.diginex.entities.Side.BUY;
import static alick.diginex.entities.Side.SELL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MatchingEngineTest {
	private final Random random = new Random();
	private MatchingEngine matchingEngine;

	@BeforeEach
	public void setup() {
		this.matchingEngine = new MatchingEngine("BTC/USD", 35000d);
		this.matchingEngine.start();
	}

	@AfterEach
	public void teardown() {
		this.matchingEngine.stop();
		this.matchingEngine = null;
	}

	@Test
	@Timeout(value = 5)
	public void submitNewBuyOrder() throws InterruptedException {
		final String clOrdId = "clOrdId-" + random.nextInt(10000);
		final Side side = BUY;
		final OrderType ordType = LIMIT;
		final double orderQty = 0.1;
		final double price = 35000d;
		final List<ResponseMessage> newReport = this.matchingEngine.submitNewOrderRequest(clOrdId, side, ordType, orderQty, price);

		assertThat("response message for new order", newReport, hasSize(1));
		assertThat("expect execution report", newReport.get(0), instanceOf(ExecutionReport.class));

		final ExecutionReport newEr = (ExecutionReport) newReport.get(0);
		assertThat("new order clOrdId", newEr.getClOrdId(), is(clOrdId));
		assertThat("new order origClOrdId", newEr.getOrigClOrdId(), nullValue());
		assertThat("new order order qty", newEr.getOrderQty(), is(orderQty));
		assertThat("new order side", newEr.getSide(), is(side));
		assertThat("new order side", newEr.getPrice(), is(price));
		assertThat("new order status", newEr.getOrderState(), is(OrderStatus.NEW));
	}

	@Test
	@Timeout(value = 5)
	public void amendOrder() throws InterruptedException {
		final String clOrdId = "clOrdId-" + random.nextInt(10000);
		final Side side = BUY;
		final OrderType ordType = LIMIT;
		final double orderQty = 0.1;
		final double price = 35000d;
		final List<ResponseMessage> newReport = this.matchingEngine.submitNewOrderRequest(clOrdId, side, ordType, orderQty, price);

		assertThat("response message for new order", newReport, hasSize(1));
		assertThat("expect execution report", newReport.get(0), instanceOf(ExecutionReport.class));

		final ExecutionReport newEr = (ExecutionReport) newReport.get(0);
		assertThat("new order status", newEr.getOrderState(), is(OrderStatus.NEW));

		final String newClOrdId = "clOrdId-" + random.nextInt(10000);
		final String origClOrdId = clOrdId;
		final OrderType newOrdType = LIMIT;
		final double newQty = 0.01;
		final double newPrice = 35000d;
		final List<ResponseMessage> amendReport = this.matchingEngine.submitAmendOrderRequest(origClOrdId, newClOrdId, side, newOrdType, newQty, newPrice);
		assertThat("response message for amend order", amendReport, hasSize(1));
		assertThat("expect execution report", amendReport.get(0), instanceOf(ExecutionReport.class));

		final ExecutionReport amendEr = (ExecutionReport) amendReport.get(0);
		assertThat("new order clOrdId", amendEr.getClOrdId(), is(newClOrdId));
		assertThat("new order origClOrdId", amendEr.getOrigClOrdId(), is(origClOrdId));
		assertThat("new order order qty", amendEr.getOrderQty(), is(newQty));
		assertThat("new order side", amendEr.getSide(), is(side));
		assertThat("new order side", amendEr.getPrice(), is(newPrice));
		assertThat("new order status", amendEr.getOrderState(), is(OrderStatus.REPLACED));
	}

	@Test
	@Timeout(value = 5)
	public void cancelOrder() throws InterruptedException {
		final String clOrdId = "clOrdId-" + random.nextInt(10000);
		final Side side = BUY;
		final OrderType ordType = LIMIT;
		final double orderQty = 0.1;
		final double price = 35000d;
		final List<ResponseMessage> newReport = this.matchingEngine.submitNewOrderRequest(clOrdId, side, ordType, orderQty, price);

		assertThat("response message for new order", newReport, hasSize(1));
		assertThat("expect execution report", newReport.get(0), instanceOf(ExecutionReport.class));

		final ExecutionReport newEr = (ExecutionReport) newReport.get(0);
		assertThat("new order status", newEr.getOrderState(), is(OrderStatus.NEW));

		final String cancelClOrdId = "clOrdId-" + random.nextInt(10000);
		final String origClOrdId = clOrdId;
		final List<ResponseMessage> cxlReport = this.matchingEngine.submitCancelOrderRequest(origClOrdId, cancelClOrdId);
		assertThat("response message for amend order", cxlReport, hasSize(1));
		assertThat("expect execution report", cxlReport.get(0), instanceOf(ExecutionReport.class));

		final ExecutionReport cxlEr = (ExecutionReport) cxlReport.get(0);
		assertThat("new order clOrdId", cxlEr.getClOrdId(), is(cancelClOrdId));
		assertThat("new order origClOrdId", cxlEr.getOrigClOrdId(), is(origClOrdId));
		assertThat("new order order qty", cxlEr.getOrderQty(), is(orderQty));
		assertThat("new order side", cxlEr.getSide(), is(side));
		assertThat("new order side", cxlEr.getPrice(), is(price));
		assertThat("new order status", cxlEr.getOrderState(), is(OrderStatus.CANCELLED));
	}

	@Test
	@Timeout(value = 5)
	public void matchingOrders() throws InterruptedException {
		// buy 100@35000
		final String clOrdIdBuy1 = "clOrdId-buy-" + random.nextInt(10000);
		final List<ResponseMessage> buyReport1 = this.matchingEngine.submitNewOrderRequest(clOrdIdBuy1, BUY, LIMIT, 100, 35000d);
		assertThat("response message for buy order 1", buyReport1, hasSize(1));
		assertThat("expect execution report", buyReport1.get(0), instanceOf(ExecutionReport.class));
		final ExecutionReport buy1Er1 = (ExecutionReport) buyReport1.get(0);
		assertThat("new buy order status", buy1Er1.getOrderState(), is(OrderStatus.NEW));

		final OrderBookSnapshot snap0 = this.matchingEngine.snapshotOrderBook();
		assertThat("snapshot after execution bid limit price ", snap0.getBidLimitQueue(), aMapWithSize(1));
		assertThat("snapshot after execution ask limit price ", snap0.getAskLimitQueue(), anEmptyMap());

		assertThat("snapshot bid queue price buckets", snap0.getBidLimitQueue().keySet(), containsInAnyOrder(35000d));
		assertThat("snapshot bid queue buckets @35000", snap0.getBidLimitQueue().get(35000d), contains(
				new OrderOpenQty(buy1Er1.getOrderId(), 100d)
		));

		// sell 120 shr -> 100 shr crossed + sell 20@35000
		final String clOrdIdSell1 = "clOrdId-sell-" + random.nextInt(10000);
		final List<ResponseMessage> sellReport1 = this.matchingEngine.submitNewOrderRequest(clOrdIdSell1, SELL, LIMIT, 120, 35000d);
		assertThat("response message for sell order 1", sellReport1, hasSize(3)); // 1x new ack + 1x buy/sell
		assertThat("expect execution report for new ack", sellReport1.get(0), instanceOf(ExecutionReport.class));
		assertThat("expect execution report for buy execution", sellReport1.get(1), instanceOf(ExecutionReport.class));
		assertThat("expect execution report for sell execution", sellReport1.get(2), instanceOf(ExecutionReport.class));
		final ExecutionReport sell1Er1 = (ExecutionReport) sellReport1.get(0);
		assertThat("new order status", sell1Er1.getOrderState(), is(OrderStatus.NEW));
		assertThat("new order status", sell1Er1.getClOrdId(), is(clOrdIdSell1));
		final ExecutionReport sell1BuyEr = (ExecutionReport) sellReport1.get(1);
		assertThat("execution report for buy trade, clOrdId", sell1BuyEr.getClOrdId(), is(clOrdIdBuy1));
		assertThat("execution report for buy trade, side", sell1BuyEr.getSide(), is(BUY));
		assertThat("execution report for buy trade, cumQty ", sell1BuyEr.getCumQty(), is(100d));
		assertThat("execution report for buy trade, orderQty", sell1BuyEr.getOrderQty(), is(100d));
		assertThat("execution report for buy trade, price", sell1BuyEr.getPrice(), is(35000d));
		assertThat("execution report for buy trade, leavesQty", sell1BuyEr.getLeavesQty(), is(0d));
		assertThat("execution report for buy trade, avgPx", sell1BuyEr.getAvgPx(), is(35000d));
		assertThat("execution report for buy trade, lastQty", sell1BuyEr.getLastQty(), is(100d));
		assertThat("execution report for buy trade, lastPx", sell1BuyEr.getLastPx(), is(35000d));
		assertThat("execution report for buy trade, order state", sell1BuyEr.getOrderState(), is(OrderStatus.FILLED));

		final ExecutionReport sell1SellEr = (ExecutionReport) sellReport1.get(2);
		assertThat("execution report for sell trade, clOrdId", sell1SellEr.getClOrdId(), is(clOrdIdSell1));
		assertThat("execution report for sell trade, side", sell1SellEr.getSide(), is(SELL));
		assertThat("execution report for sell trade, cumQty ", sell1SellEr.getCumQty(), is(100d));
		assertThat("execution report for sell trade, orderQty", sell1SellEr.getOrderQty(), is(120d));
		assertThat("execution report for sell trade, price", sell1SellEr.getPrice(), is(35000d));
		assertThat("execution report for sell trade, leavesQty", sell1SellEr.getLeavesQty(), is(120d - 100d));
		assertThat("execution report for sell trade, avgPx", sell1SellEr.getAvgPx(), is(35000d));
		assertThat("execution report for sell trade, lastQty", sell1SellEr.getLastQty(), is(100d));
		assertThat("execution report for sell trade, lastPx", sell1SellEr.getLastPx(), is(35000d));
		assertThat("execution report for sell trade, order state", sell1SellEr.getOrderState(), is(OrderStatus.PARTIAL_FILLLED));

		final OrderBookSnapshot snap1 = this.matchingEngine.snapshotOrderBook();
		assertThat("snapshot after execution bid limit price ", snap1.getBidLimitQueue(), anEmptyMap());
		assertThat("snapshot after execution ask limit price ", snap1.getAskLimitQueue(), aMapWithSize(1));

		assertThat("snapshot after execution ask queue price buckets", snap1.getAskLimitQueue().keySet(), containsInAnyOrder(35000d));
		assertThat("snapshot after execution ask queue buckets @35000", snap1.getAskLimitQueue().get(35000d), contains(
				new OrderOpenQty(sell1Er1.getOrderId(), 20d)
		));

		// order 2 sell 140@36000 =>  20@35000, 140@36000
		final String clOrdIdSell2 = "clOrdId-sell-" + random.nextInt(10000);
		final List<ResponseMessage> sellReport2 = this.matchingEngine.submitNewOrderRequest(clOrdIdSell2, SELL, LIMIT, 140, 36000d);
		assertThat("response message for sell order 2", sellReport2, hasSize(1));
		final ExecutionReport sell2Er1 = (ExecutionReport) sellReport2.get(0);
		assertThat("new order status", sell2Er1.getOrderState(), is(OrderStatus.NEW));
		assertThat("new order status", sell2Er1.getClOrdId(), is(clOrdIdSell2));

		final OrderBookSnapshot snap2 = this.matchingEngine.snapshotOrderBook();
		assertThat("snapshot after execution bid limit price ", snap2.getBidLimitQueue(), anEmptyMap());
		assertThat("snapshot after execution ask limit price ", snap2.getAskLimitQueue(), aMapWithSize(2));

		assertThat("snapshot after execution ask queue price buckets", snap2.getAskLimitQueue().keySet(), containsInAnyOrder(36000d, 35000d));
		assertThat("snapshot after execution ask queue buckets @35000", snap2.getAskLimitQueue().get(35000d), contains(
				new OrderOpenQty(sell1Er1.getOrderId(), 20d)
		));
		assertThat("snapshot after execution ask queue buckets @36000", snap2.getAskLimitQueue().get(36000d), contains(
				new OrderOpenQty(sell2Er1.getOrderId(), 140d)
		));

		// buy order 200@36500
		final String clOrdIdBuy2 = "clOrdId-buy-" + random.nextInt(10000);
		final List<ResponseMessage> buyReport2 = this.matchingEngine.submitNewOrderRequest(clOrdIdBuy2, BUY, LIMIT, 200, 36500d);
		assertThat("response message for buy order 2", buyReport2, hasSize(5)); // 1x new ack+ 2x buy/see
		final ExecutionReport buy2Er1 = (ExecutionReport) buyReport2.get(0);
		assertThat("new order status", buy2Er1.getOrderState(), is(OrderStatus.NEW));
		assertThat("new order clOrdId", buy2Er1.getClOrdId(), is(clOrdIdBuy2));
		assertThat("new order Side", buy2Er1.getSide(), is(BUY));
		assertThat("new order OrdType", buy2Er1.getOrderType(), is(LIMIT));
		assertThat("new order OrderQty", buy2Er1.getOrderQty(), is(200d));
		assertThat("new order Price", buy2Er1.getPrice(), is(36500d));
		assertThat("new order LeavesQty", buy2Er1.getLeavesQty(), is(200d));

		final ExecutionReport buy2BuyEr1 = (ExecutionReport) buyReport2.get(1); // buy 20@35000,  this order
		assertThat("execution report for buy trade, clOrdId", buy2BuyEr1.getClOrdId(), is(clOrdIdBuy2));
		assertThat("execution report for buy trade, clOrdId", buy2BuyEr1.getOrigClOrdId(), nullValue());
		assertThat("execution report for buy trade, side", buy2BuyEr1.getSide(), is(BUY));
		assertThat("execution report for buy trade, cumQty ", buy2BuyEr1.getCumQty(), is(20d));
		assertThat("execution report for buy trade, orderQty", buy2BuyEr1.getOrderQty(), is(200d));
		assertThat("execution report for buy trade, price", buy2BuyEr1.getPrice(), is(36500d));
		assertThat("execution report for buy trade, leavesQty", buy2BuyEr1.getLeavesQty(), is(200d - 20d));
		assertThat("execution report for buy trade, avgPx", buy2BuyEr1.getAvgPx(), is(35000d));
		assertThat("execution report for buy trade, lastQty", buy2BuyEr1.getLastQty(), is(20d));
		assertThat("execution report for buy trade, lastPx", buy2BuyEr1.getLastPx(), is(35000d));
		assertThat("execution report for buy trade, order state", buy2BuyEr1.getOrderState(), is(OrderStatus.PARTIAL_FILLLED));

		final ExecutionReport buy2SellEr1 = (ExecutionReport) buyReport2.get(2); // sell 20@35000, sell ord 1
		assertThat("execution report for sell trade, clOrdId", buy2SellEr1.getClOrdId(), is(clOrdIdSell1));
		assertThat("execution report for sell trade, clOrdId", buy2SellEr1.getOrigClOrdId(), nullValue());
		assertThat("execution report for sell trade, side", buy2SellEr1.getSide(), is(SELL));
		assertThat("execution report for sell trade, cumQty ", buy2SellEr1.getCumQty(), is(100d + 20d));
		assertThat("execution report for sell trade, orderQty", buy2SellEr1.getOrderQty(), is(120d));
		assertThat("execution report for sell trade, price", buy2SellEr1.getPrice(), is(35000d));
		assertThat("execution report for sell trade, leavesQty", buy2SellEr1.getLeavesQty(), is(120d - 100d - 20d));
		assertThat("execution report for sell trade, avgPx", buy2SellEr1.getAvgPx(), is((35000d * 100 + 35000d * 20) / (100 + 20)));
		assertThat("execution report for sell trade, lastQty", buy2SellEr1.getLastQty(), is(20d));
		assertThat("execution report for sell trade, lastPx", buy2SellEr1.getLastPx(), is(35000d));
		assertThat("execution report for sell trade, order state", buy2SellEr1.getOrderState(), is(OrderStatus.FILLED));

		final ExecutionReport buy2BuyEr2 = (ExecutionReport) buyReport2.get(3); // buy 140@36000   this order
		assertThat("execution report for buy trade, clOrdId", buy2BuyEr2.getClOrdId(), is(clOrdIdBuy2));
		assertThat("execution report for buy trade, clOrdId", buy2BuyEr2.getOrigClOrdId(), nullValue());
		assertThat("execution report for buy trade, side", buy2BuyEr2.getSide(), is(BUY));
		assertThat("execution report for buy trade, cumQty ", buy2BuyEr2.getCumQty(), is(20d + 140d));
		assertThat("execution report for buy trade, orderQty", buy2BuyEr2.getOrderQty(), is(200d));
		assertThat("execution report for buy trade, price", buy2BuyEr2.getPrice(), is(36500d));
		assertThat("execution report for buy trade, leavesQty", buy2BuyEr2.getLeavesQty(), is(200d - 20d - 140d));
		assertThat("execution report for buy trade, avgPx", buy2BuyEr2.getAvgPx(), is((35000d * 20d + 36000d * 140d) / (20d + 140d)));
		assertThat("execution report for buy trade, lastQty", buy2BuyEr2.getLastQty(), is(140d));
		assertThat("execution report for buy trade, lastPx", buy2BuyEr2.getLastPx(), is(36000d));
		assertThat("execution report for buy trade, order state", buy2BuyEr2.getOrderState(), is(OrderStatus.PARTIAL_FILLLED));

		final ExecutionReport buy2SellEr2 = (ExecutionReport) buyReport2.get(4); // sell 140@36000  sell ord 2
		assertThat("execution report for sell trade, clOrdId", buy2SellEr2.getClOrdId(), is(clOrdIdSell2));
		assertThat("execution report for sell trade, clOrdId", buy2SellEr2.getOrigClOrdId(), nullValue());
		assertThat("execution report for sell trade, side", buy2SellEr2.getSide(), is(SELL));
		assertThat("execution report for sell trade, cumQty ", buy2SellEr2.getCumQty(), is(140d));
		assertThat("execution report for sell trade, orderQty", buy2SellEr2.getOrderQty(), is(140d));
		assertThat("execution report for sell trade, price", buy2SellEr2.getPrice(), is(36000d));
		assertThat("execution report for sell trade, leavesQty", buy2SellEr2.getLeavesQty(), is(0d));
		assertThat("execution report for sell trade, avgPx", buy2SellEr2.getAvgPx(), is(36000d));
		assertThat("execution report for sell trade, lastQty", buy2SellEr2.getLastQty(), is(140d));
		assertThat("execution report for sell trade, lastPx", buy2SellEr2.getLastPx(), is(36000d));
		assertThat("execution report for sell trade, order state", buy2SellEr2.getOrderState(), is(OrderStatus.FILLED));

		final OrderBookSnapshot snap3 = this.matchingEngine.snapshotOrderBook();
		assertThat("snapshot after execution bid limit price ", snap3.getBidLimitQueue(), aMapWithSize(2));
		assertThat("snapshot after execution ask limit price ", snap3.getAskLimitQueue(), anEmptyMap());

		assertThat("snapshot after execution bid queue price buckets", snap3.getBidLimitQueue().keySet(), containsInAnyOrder(35000d, 36500d));
		assertThat("snapshot after execution bid queue buckets @35000", snap3.getBidLimitQueue().get(35000d), empty());
		assertThat("snapshot after execution bid queue buckets @36000", snap3.getBidLimitQueue().get(36500d), contains(
				new OrderOpenQty(buy2Er1.getOrderId(), 200d - 140d - 20d)
		));
	}
}
