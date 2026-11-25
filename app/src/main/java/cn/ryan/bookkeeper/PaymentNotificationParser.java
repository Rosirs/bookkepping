package cn.ryan.bookkeeper;

import java.util.*;
import java.util.regex.*;

/** Conservative local rules. All results require review; notification text is not proof of payment. */
final class PaymentNotificationParser {
    static final String WECHAT="com.tencent.mm", ALIPAY="com.eg.android.AlipayGphone";
    static final String RULE="payment-notification-v1";
    record Result(long fen,String type,String evidence,String reason) {}
    private static final Pattern MONEY=Pattern.compile("(?:[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.])|(?<![0-9.])([0-9]+(?:\\.[0-9]{1,2})?)\\s*元)");
    static String source(String pkg) {return WECHAT.equals(pkg)?"微信":ALIPAY.equals(pkg)?"支付宝":"未知来源";}
    static Result parse(String pkg,String title,String body) {
        if(!WECHAT.equals(pkg)&&!ALIPAY.equals(pkg))return null;
        title=title==null?"":title;body=body==null?"":body;
        String text=title+"\n"+body;
        if(text.length()>4000||text.matches("(?s).*(验证码|校验码|动态密码|登录验证).*"))return null;
        // Restrict to payment-labelled titles; ordinary chats mentioning a price are ignored.
        if(!title.matches("(?s).*(微信支付|支付宝|收款通知|付款成功|支付成功|交易提醒|动账提醒|退款通知).*"))return null;
        if(!text.matches("(?s).*(支付|付款|收款|到账|支出|收入|消费|退款|转账|还款|提现).*"))return null;
        Set<Long> amounts=new HashSet<>();Matcher m=MONEY.matcher(text);
        while(m.find())try{amounts.add(LedgerRules.parseAmount(m.group(1)!=null?m.group(1):m.group(2)));}catch(IllegalArgumentException ignored){}
        long fen=amounts.size()==1?amounts.iterator().next():0;
        boolean special=text.matches("(?s).*(退款|退回|撤销|失败|关闭|还款|提现|转账|余额|剩余|待支付|待付款|待收款|申请|处理中).*"), income=text.matches("(?s).*(收款|到账|收入).*"), expense=text.matches("(?s).*(支付成功|付款成功|成功付款|成功支付|支出|消费).*" );
        String type=!special&&income!=expense?(income?"income":"expense"):"";
        String reason=special?"特殊交易或包含余额，请核对类型及实际金额":type.isEmpty()?"收支方向不明确，请选择类型":"请核对金额、日期与分类";
        if(fen==0)reason="金额缺失或出现多个金额，请补充核对；"+reason;
        String evidence=text.replaceAll("[0-9]{7,}","••••");
        if(evidence.length()>700)evidence=evidence.substring(0,700);
        return new Result(fen,type,evidence,reason);
    }
}
