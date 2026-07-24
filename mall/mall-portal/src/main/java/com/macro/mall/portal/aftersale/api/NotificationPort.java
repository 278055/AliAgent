package com.macro.mall.portal.aftersale.api;

public interface NotificationPort {
    StepResult notify(NotificationCommand command);
}
