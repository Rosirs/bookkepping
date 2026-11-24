package cn.ryan.bookkeeper;

import java.time.LocalDate;
import java.time.YearMonth;

/** Calendar-based budget arithmetic. Current-month days include today. */
final class BudgetStatus {
    final long limit, spent, remaining, dailyAllowance;
    final int daysLeft, progress;
    final boolean configured, exceeded;
    BudgetStatus(YearMonth month,long limit,long spent,LocalDate today) {
        this.limit=limit;this.spent=spent;configured=limit>0;
        remaining=configured?limit-spent:0;exceeded=configured&&remaining<0;
        YearMonth current=YearMonth.from(today);
        daysLeft=month.isBefore(current)?0:month.equals(current)?month.lengthOfMonth()-today.getDayOfMonth()+1:month.lengthOfMonth();
        dailyAllowance=configured&&daysLeft>0?Math.max(0,remaining)/daysLeft:0;
        progress=configured?(int)Math.min(1000,1000d*spent/limit):0;
    }
}
