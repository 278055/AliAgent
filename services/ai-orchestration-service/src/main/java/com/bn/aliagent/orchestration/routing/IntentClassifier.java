package com.bn.aliagent.orchestration.routing;

@FunctionalInterface
public interface IntentClassifier {
    Intent classify(String input);
}
