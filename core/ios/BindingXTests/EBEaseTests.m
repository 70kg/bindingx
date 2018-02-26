//
//  EaseTests.m
//  BindingXTests
//
//  Created by 对象 on 2018/2/25.
//  Copyright © 2018年 Alibaba. All rights reserved.
//

#import "EBTestCase.h"

@interface EBEaseTests : EBTestCase

@end

@implementation EBEaseTests


- (void)testExample {
    
    //linear(t,0,500,2000)
    EBExpression *expression = [EBTestUtils expressionFromJSON:@"{\"type\":\"CallExpression\",\"children\":[{\"type\":\"Identifier\",\"value\":\"linear\"},{\"type\":\"Arguments\",\"children\":[{\"type\":\"Identifier\",\"value\":\"t\"},{\"type\":\"NumericLiteral\",\"value\":0},{\"type\":\"NumericLiteral\",\"value\":500},{\"type\":\"NumericLiteral\",\"value\":2000}]}]}"];
    
//    self.scope[@"t"] = @(1000);
//    NSObject *result = [expression executeInScope:self.scope];
//    XCTAssertEqualObjects(result, @(250));
    
    [self AssertEqualObjects:@(250)
                   withNewScope:@{@"t":@(1000)}
                  expression:expression];
}

@end
