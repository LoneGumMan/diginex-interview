package alick.diginex.entities;

public enum OrderType {
	LIMIT,
	MARKET,
	;
	public static boolean isMarketOrder(OrderType orderType) {
		return MARKET == orderType;
	}
}