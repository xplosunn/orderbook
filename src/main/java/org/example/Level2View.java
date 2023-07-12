package org.example;

import java.math.BigDecimal;

public interface Level2View {

    public enum Side {
        BID, ASK;
    }

    void onNewOrder(Side side, BigDecimal price, long quantity, long orderId);

    void onCancelOrder(long orderId);

    void onReplaceOrder(BigDecimal price, long quantity, long orderId);

    // When an aggressor order crosses the spread, it will be matched with an existing resting order, causing a trade.
    // The aggressor order will NOT cause an invocation of onNewOrder.
    void onTrade(long quantity, long restingOrderId);

    long getSizeForPriceLevel(Side side, BigDecimal price); // total quantity of existing orders on this price level

    long getBookDepth(Side side); // get the number of price levels on the specified side

    BigDecimal getTopOfBook(Side side); // get highest bid or lowest ask, resp.
}
