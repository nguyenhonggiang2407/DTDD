package com.example.courtmatepickleballcourtmanager.util;

import com.example.courtmatepickleballcourtmanager.model.Promotion;
import com.example.courtmatepickleballcourtmanager.model.SpecialDate;

import java.util.List;

public final class PromotionEngine {

    private PromotionEngine() {
    }

    public static class Result {
        public Promotion promotion;
        public String code;
        public int discountCourt;
        public int discountService;
        public int totalDiscount;

        public boolean hasDiscount() {
            return totalDiscount > 0;
        }
    }

    public static Result bestForSession(List<Promotion> promotions, int courtAmount,
                                        int serviceAmount, String date, Promotion codePromotion,
                                        String code) {
        Result best = new Result();
        if (promotions != null) {
            for (Promotion promotion : promotions) {
                Result candidate = apply(promotion, courtAmount, serviceAmount, date, null);
                if (candidate != null && candidate.totalDiscount > best.totalDiscount) {
                    best = candidate;
                }
            }
        }
        if (codePromotion != null && codePromotion.status.equals(Promotion.STATUS_ACTIVE)) {
            Result candidate = apply(codePromotion, courtAmount, serviceAmount, date, code);
            if (candidate != null && candidate.totalDiscount > best.totalDiscount) {
                best = candidate;
            }
        }
        return best;
    }

    public static Result bestForScope(List<Promotion> promotions, String scope, int amount,
                                      String date, Promotion codePromotion, String code) {
        Result best = new Result();
        if (promotions != null) {
            for (Promotion promotion : promotions) {
                Result candidate = applyForScope(promotion, scope, amount, date, null);
                if (candidate != null && candidate.totalDiscount > best.totalDiscount) {
                    best = candidate;
                }
            }
        }
        if (codePromotion != null) {
            Result candidate = applyForScope(codePromotion, scope, amount, date, code);
            if (candidate != null && candidate.totalDiscount > best.totalDiscount) {
                best = candidate;
            }
        }
        return best;
    }

    public static Result applyForScope(Promotion promotion, String scope, int amount,
                                       String date, String code) {
        if (promotion == null || !isActiveOn(promotion, date)
                || !isScope(promotion, scope)) {
            return null;
        }
        if (amount <= 0 || amount < promotion.minOrder) {
            return null;
        }
        int value = discount(promotion, amount);
        if (value <= 0) {
            return null;
        }
        Result result = new Result();
        result.promotion = promotion;
        result.code = code;
        result.totalDiscount = value;
        return result;
    }

    public static Result apply(Promotion promotion, int courtAmount, int serviceAmount,
                               String date, String code) {
        if (promotion == null || !isActiveOn(promotion, date)) {
            return null;
        }
        if (!isScope(promotion, Promotion.SCOPE_COURT)
                && !isScope(promotion, Promotion.SCOPE_SERVICE)
                && !isScope(promotion, Promotion.SCOPE_ALL)) {
            return null;
        }
        int base = 0;
        if (isScope(promotion, Promotion.SCOPE_COURT)) {
            base = courtAmount;
        } else if (isScope(promotion, Promotion.SCOPE_SERVICE)) {
            base = serviceAmount;
        } else {
            base = courtAmount + serviceAmount;
        }
        if (base <= 0 || base < promotion.minOrder) {
            return null;
        }
        int discount = discount(promotion, base);
        if (discount <= 0) {
            return null;
        }
        Result result = new Result();
        result.promotion = promotion;
        result.code = code;
        if (isScope(promotion, Promotion.SCOPE_COURT)) {
            result.discountCourt = Math.min(discount, courtAmount);
        } else if (isScope(promotion, Promotion.SCOPE_SERVICE)) {
            result.discountService = Math.min(discount, serviceAmount);
        } else {
            result.discountCourt = Math.min(discount, courtAmount);
            result.discountService = Math.min(discount - result.discountCourt, serviceAmount);
        }
        result.totalDiscount = result.discountCourt + result.discountService;
        return result;
    }

    public static int discountForAmount(Promotion promotion, int amount) {
        if (promotion == null || amount <= 0 || amount < promotion.minOrder) {
            return 0;
        }
        return discount(promotion, amount);
    }

    public static boolean isActiveOn(Promotion promotion, String date) {
        if (promotion == null || !Promotion.STATUS_ACTIVE.equals(promotion.status)) {
            return false;
        }
        if (promotion.fromDate != null && !promotion.fromDate.isEmpty()
                && date.compareTo(promotion.fromDate) < 0) {
            return false;
        }
        return promotion.toDate == null || promotion.toDate.isEmpty()
                || date.compareTo(promotion.toDate) <= 0;
    }

    public static boolean isScope(Promotion promotion, String scope) {
        return promotion.scope.equals(scope) || promotion.scope.equals(Promotion.SCOPE_ALL);
    }

    public static String scopeLabel(String scope) {
        switch (scope) {
            case Promotion.SCOPE_COURT:
                return "Sân";
            case Promotion.SCOPE_SERVICE:
                return "Dịch vụ";
            case Promotion.SCOPE_CLASS:
                return "Lớp học";
            case Promotion.SCOPE_EVENT:
                return "Sự kiện";
            default:
                return "Tất cả";
        }
    }

    public static String discountLabel(Promotion promotion) {
        if (promotion.isPercent()) {
            return promotion.value + "%";
        }
        return MoneyUtils.format(promotion.value);
    }

    public static boolean isSpecialDateOn(SpecialDate specialDate, String date) {
        if (specialDate == null || !SpecialDate.STATUS_ACTIVE.equals(specialDate.status)) {
            return false;
        }
        int month = Integer.parseInt(date.substring(5, 7));
        int day = Integer.parseInt(date.substring(8, 10));
        int year = Integer.parseInt(date.substring(0, 4));
        if (specialDate.month != month || specialDate.day != day) {
            return false;
        }
        return specialDate.annual == 1 || specialDate.year == year;
    }

    private static int discount(Promotion promotion, int amount) {
        int value;
        if (promotion.isPercent()) {
            value = (int) (amount * promotion.value / 100.0);
            if (promotion.maxDiscount > 0) {
                value = Math.min(value, promotion.maxDiscount);
            }
        } else {
            value = promotion.value;
        }
        return Math.max(0, Math.min(value, amount));
    }
}
