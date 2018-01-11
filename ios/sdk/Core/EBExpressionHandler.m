/**
 * Copyright 2017 Alibaba Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import "EBExpressionHandler.h"
#import "EBExpressionGesture.h"
#import "EBExpressionScroller.h"
#import "EBExpression.h"
#import "EBExpressionProperty.h"
#import "EBExpressionExecutor.h"
#import "EBExpressionScope.h"
#import "EBExpressionTiming.h"
#import "EBExpressionOrientation.h"
#import <JavaScriptCore/JavaScriptCore.h>
#import "EBUtility.h"

@interface EBExpressionHandler ()

@end

@implementation EBExpressionHandler

- (void)dealloc
{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (instancetype)initWithExpressionType:(WXExpressionType)exprType
                                source:(id)source {
    if (self = [super init]) {
        self.source = source;
        self.exprType = exprType;
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(removeExpressionBinding) name:@"WXExpressionBindingRemove" object:nil];
    }
    return self;
}

- (void)updateTargets:(NSMapTable<NSString *, id> *)targets
           expression:(NSDictionary<NSString *, NSDictionary *> *)targetExpression
         options:(NSDictionary *)options
       exitExpression:(NSString *)exitExpression
             callback:(KeepAliveCallback)callback {
    self.targets = targets;
    self.expressions = targetExpression;
    self.exitExpression = exitExpression;
    self.callback = callback;
    self.options = options;
}

- (void)removeExpressionBinding {
    self.targets = nil;
    self.expressions = nil;
}

+ (WXExpressionType)stringToExprType:(NSString *)typeStr {
    if ([@"pan" isEqualToString:typeStr]) {
        return WXExpressionTypePan;
    } else if ([@"scroll" isEqualToString:typeStr]) {
        return WXExpressionTypeScroll;
    } else if ([@"timing" isEqualToString:typeStr]) {
        return WXExpressionTypeTiming;
    } else if ([@"orientation" isEqualToString:typeStr]) {
        return WXExpressionTypeOrientation;
    }
    return WXExpressionTypeUndefined;
}

+ (EBExpressionHandler *)handlerWithExpressionType:(WXExpressionType)exprType
                                            source:(id)source {
    switch (exprType) {
        case WXExpressionTypePan:
            return [[EBExpressionGesture alloc] initWithExpressionType:exprType source:source];
        case WXExpressionTypeScroll:
            return [[EBExpressionScroller alloc] initWithExpressionType:exprType source:source];
        case WXExpressionTypeTiming:
            return [[EBExpressionTiming alloc] initWithExpressionType:exprType source:source];
        case WXExpressionTypeOrientation:
            return [[EBExpressionOrientation alloc] initWithExpressionType:exprType source:source];
        default:
            return [EBExpressionHandler new];
    }
}

- (BOOL)shouldExit:(NSDictionary *)scope {
    NSString* exitExpressionTransformed = self.exitExpression;
    if ([exitExpressionTransformed isKindOfClass:NSString.class]) {
        if( [EBUtility isBlankString:exitExpressionTransformed]) {
            return NO;
        }
    } else if ([exitExpressionTransformed isKindOfClass:NSDictionary.class]) {
        exitExpressionTransformed = (NSString *)((NSDictionary *)exitExpressionTransformed)[@"transformed"];
    }
    
    NSDictionary *expressionTree  = [NSJSONSerialization JSONObjectWithData:[exitExpressionTransformed dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingAllowFragments error:nil];
    if (!expressionTree || expressionTree.count == 0) {
        return NO;
    }
    
    NSObject *result = [[[EBExpression alloc] initWithRoot:expressionTree] executeInScope:scope];
    if (!result) {
        return NO;
    }
    
    if ([result isKindOfClass:[NSNumber class]]) {
        return [(NSNumber *)result boolValue];
    } else if ([result isKindOfClass:[NSString class]]) {
        return [(NSString *)result boolValue];
    }
    
    return NO;
}

- (BOOL)executeExpression:(NSDictionary *)scope {
    
    for (NSString *targetRef in self.expressions) {
        id target = [self.targets objectForKey:targetRef];
        if (!target) {
            continue;
        }
        
        NSDictionary *epMap = self.expressions[targetRef];
        EBExpressionProperty *model = [[EBExpressionProperty alloc] init];
        
        // gather property
        for (NSString *property in epMap) {
            NSDictionary *expressionDic = epMap[property];
            id expression = expressionDic[@"expression"];
            NSDictionary *config = expressionDic[@"config"];
            
            NSDictionary* expressionTree = nil;
            NSString* originExpression = nil;
            if ([expression isKindOfClass:NSString.class]) {
                // expressionbinding V1
                expressionTree = [NSJSONSerialization JSONObjectWithData:[(NSString *)expression dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingAllowFragments error:nil];
            } else if ([expression isKindOfClass:NSDictionary.class]) {
                // expressionbinding V2
                expressionTree = [NSJSONSerialization JSONObjectWithData:[(NSString *)(NSDictionary *)expression[@"transformed"] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingAllowFragments error:nil];
                originExpression = (NSString *)(NSDictionary *)expression[@"origin"];
            }
            NSObject *result = nil;
            if (expressionTree && expressionTree.count > 0) {
                result = [[[EBExpression alloc] initWithRoot:expressionTree] executeInScope:scope];
            } else if (originExpression) {
                JSContext* context = [JSContext new];
                for (NSString *key in scope) {
                    [context setObject:scope[key] forKeyedSubscript:key];
                }
                result = [[context evaluateScript:originExpression] toObject];
            }
            if (result) {
                [EBExpressionExecutor change:&model property:property config:config to:result];
            }
        }
        
        // execute
        [EBExpressionExecutor execute:model to:target];
    }
    
    // exit expression
    if ([self shouldExit:scope]) {
        return NO;
    }
    return YES;
}

- (NSMutableDictionary *)generalScope {
    NSMutableDictionary *scope = [EBExpressionScope generalScope];
    return scope;
}

@end
