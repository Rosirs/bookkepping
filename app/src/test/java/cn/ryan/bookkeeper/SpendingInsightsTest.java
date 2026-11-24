package cn.ryan.bookkeeper;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class SpendingInsightsTest {
    @Test public void ratiosUseFoodSelectionAndAllowNegativeBalance(){
        Map<String,Long> amounts=new HashMap<>();amounts.put("餐饮",3300L);amounts.put("购物",6700L);
        SpendingInsights result=new SpendingInsights(8000,10000,amounts,SpendingInsights.DEFAULT_FOOD,3);
        assertEquals("33.0%",result.engel);assertEquals("-25.0%",result.surplus);assertEquals(3333,result.averageExpense);
        result=new SpendingInsights(8000,10000,amounts,Collections.singleton("购物"),3);assertEquals("67.0%",result.engel);
    }
    @Test public void missingDenominatorsAreUnavailableAndEmptyFoodIsZero(){
        SpendingInsights empty=new SpendingInsights(0,0,Collections.emptyMap(),Collections.emptySet(),0);
        assertEquals("暂无",empty.engel);assertEquals("暂无",empty.surplus);assertEquals(0,empty.averageExpense);
        SpendingInsights spending=new SpendingInsights(0,100,Collections.singletonMap("餐饮",100L),Collections.emptySet(),1);
        assertEquals("0.0%",spending.engel);assertEquals("暂无",spending.surplus);
    }
    @Test public void percentagesRoundWithoutOverflow(){
        SpendingInsights result=new SpendingInsights(999999999999L,999999999999L,Collections.singletonMap("餐饮",999999999999L),SpendingInsights.DEFAULT_FOOD,1);
        assertEquals("100.0%",result.engel);assertEquals("0.0%",result.surplus);
        result=new SpendingInsights(3,3,Collections.singletonMap("餐饮",1L),SpendingInsights.DEFAULT_FOOD,1);assertEquals("33.3%",result.engel);
    }
}
