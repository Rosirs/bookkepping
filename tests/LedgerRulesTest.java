import cn.ryan.bookkeeper.LedgerRules;

public class LedgerRulesTest {
    private static int checks=0;
    static void equal(Object expected,Object value) { checks++; if(!expected.equals(value))throw new AssertionError(expected+" != "+value); }
    static void rejected(Runnable call) { checks++;try {call.run();} catch(RuntimeException e) {return;}throw new AssertionError("Invalid input accepted"); }
    public static void main(String[] args) {
        equal(29L,LedgerRules.parseAmount("0.29"));
        equal(10010L,LedgerRules.parseAmount("100.10"));
        equal(999999999999L,LedgerRules.parseAmount("9999999999.99"));
        equal("0.29",LedgerRules.money(29));equal("-0.01",LedgerRules.money(-1));
        equal(30L,LedgerRules.parseAmount("0.10")+LedgerRules.parseAmount("0.20"));
        for(String value:new String[]{"0","-1","1.001","NaN","1e3","10000000000"," 12","1.",".5"})rejected(()->LedgerRules.parseAmount(value));
        LedgerRules.validate("expense",1,"2024-02-29","餐饮","");checks++;
        rejected(()->LedgerRules.validate("expense",1,"2025-02-29","餐饮",""));
        rejected(()->LedgerRules.validate("transfer",1,"2026-01-01","餐饮",""));
        rejected(()->LedgerRules.validate("income",0,"2026-01-01","工资",""));
        rejected(()->LedgerRules.validate("invest",1,"2026-01-01"," ",""));
        rejected(()->LedgerRules.validate("redeem",1,"2026-01-01","投资","x".repeat(501)));
        rejected(()->LedgerRules.month("2026-13"));equal("2026-11",LedgerRules.month("2026-11"));
        System.out.println("Passed "+checks+" money/date/input checks");
    }
}
