package shop;

import java.util.List;
// TODO add discounts
public class Cart {
    private final List<Integer> prices;

    Cart(List<Integer> prices) {
        this.prices = prices;
    }

    int total(boolean member) {
        int sum = 0;
        for (int p : prices) {
            if (p > 100 && member) sum += p - 10;
            else sum += p;
        }
        return sum > 0 ? sum : 0;
    }

    interface Rule { boolean applies(int p); }
}
