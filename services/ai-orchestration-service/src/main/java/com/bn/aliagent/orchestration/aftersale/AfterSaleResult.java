package com.bn.aliagent.orchestration.aftersale;

import com.bn.aliagent.orchestration.confirmation.ConfirmationCard;
import java.util.List;

public record AfterSaleResult(AfterSaleStatus status, List<String> missingFields, ConfirmationCard confirmationCard,
                              String message) { }
