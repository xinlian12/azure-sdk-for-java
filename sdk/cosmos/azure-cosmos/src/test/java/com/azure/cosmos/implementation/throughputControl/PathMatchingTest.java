package com.azure.cosmos.implementation.throughputControl;

import org.testng.annotations.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathMatchingTest {

    @Test
    public void patchExpressionMatching() {
        String patchOperationExpr = "col[(](.*)[)].(add|remove|set|replace|increment)(.where[(](.*)(?:[)]))?$";
        Pattern pattern = Pattern.compile(patchOperationExpr);

        String matchContext = "col(column1).add.where(column1 > 2)";
        String matchContext2 = "col(column1).remove";

        Matcher matcher = pattern.matcher(matchContext);
        System.out.println(matcher.matches());
        System.out.println(matcher.groupCount());
        System.out.println(matcher.group(0));
        System.out.println(matcher.group(1));
        System.out.println(matcher.group(2));
        System.out.println(matcher.group(3));
        System.out.println(matcher.group(4));

        Matcher matcher2 = pattern.matcher(matchContext2);
        System.out.println(matcher2.matches());
        System.out.println(matcher2.groupCount());
        System.out.println(matcher2.group(0));
        System.out.println(matcher2.group(1));
        System.out.println(matcher2.group(2));
        System.out.println(matcher2.group(3));

    }
}
