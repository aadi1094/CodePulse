package shop;                                   // line 1

import java.util.List;                          // line 3  single type
import java.util.*;                             // line 4  wildcard
import static java.util.Map.entry;              // line 5  static single member
import static java.util.Collections.*;          // line 6  static wildcard (counts as static)
import shop.model.Order;                        // line 7  single type
import shop.model.Order.Line;                   // line 8  nested type: not resolvable in the MVP
import shop.model.Order;                        // line 9  duplicate of line 7

/** Expected declared types: shop.Shop, shop.Helper, shop.Kind, shop.Money, shop.Tag (not Inner). */
public class Shop {
    class Inner {
    }
}

interface Helper {
}

enum Kind {
    RETAIL
}

record Money(long cents) {
}

@interface Tag {
}
