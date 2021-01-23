package alick.diginex.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderEntryTest {
    public static final double MIN_ORDER_QTY = 100;
    private OrderEntry orderEntry;
    private static final Random rand = new Random();

    @BeforeEach
    public void setup() {
        final double orderQty = MIN_ORDER_QTY + rand.nextInt(200);
        this.orderEntry = new OrderEntry(rand.nextLong(), orderQty);
    }

    public static Stream<Arguments> badQtySource() {
        return Stream.of(
                Arguments.of(0.0d),
                Arguments.of(-1.0d * (rand.nextInt(Integer.MAX_VALUE - 1) + 1.0d))
        );
    }

    @ParameterizedTest(name = "order qty = {0}")
    @MethodSource("badQtySource")
    public void badOrderQtyShouldThrow(final double orderQty) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderEntry(rand.nextLong(), orderQty),
                "negative order qty");
    }

    @Test
    public void fullyFilledOrderIsDone() {
        final double qtyToTake = this.orderEntry.getRemainingQuantity();
        final double qtyTaken = this.orderEntry.takeQuantity(qtyToTake);

        assertThat("quantity taken = full quantity", qtyTaken, is(qtyToTake));
        assertThat("remaining qty after fully fill", this.orderEntry.getRemainingQuantity(), is(0.0d));
        assertThat("order is done after taking all remaining qty", this.orderEntry.isOrderDone());
    }

    @Test
    public void takePartialQty() {
        final double qtyToTake = MIN_ORDER_QTY;
        final double qtyTaken = this.orderEntry.takeQuantity(qtyToTake);

        assertThat("quantity taken", qtyTaken, is(qtyToTake));
        assertThat("remaining qty > 0", this.orderEntry.getRemainingQuantity(), greaterThan(0.0d));
        assertThat("order not done yet", this.orderEntry.isOrderDone(), not(true));
    }

    @Test
    public void tryToTakeMoreThanRemainingQty() {
        final double qtyToTake = this.orderEntry.getRemainingQuantity() + rand.nextInt(100) + 1;
        final double origRemainingQty = this.orderEntry.getRemainingQuantity();
        final double qtyTaken = this.orderEntry.takeQuantity(qtyToTake);

        assertThat("quantity taken", qtyTaken, is(origRemainingQty));
        assertThat("remaining qty > 0", this.orderEntry.getRemainingQuantity(), is(0.0d));
        assertThat("order not done yet", this.orderEntry.isOrderDone(), is(true));
    }
}
