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

#import "RNEBModule.h"
#import "EBExpressionHandler.h"
#import <pthread/pthread.h>
#import "EBUtility+RN.h"
#import <React/RCTUIManager.h>
#import <React/RCTUIManagerUtils.h>
#import <React/RCTText.h>
#import <React/RCTShadowText.h>

#define BINDING_EVENT_NAME @"bindingx:statechange"

@interface RNEBModule ()

@property (nonatomic, strong) NSMutableDictionary<NSString *, NSMutableDictionary<NSNumber *, EBExpressionHandler *> *> *sourceMap;

@end

@implementation RNEBModule {
    pthread_mutex_t mutex;
    pthread_mutexattr_t mutexAttr;
}

RCT_EXPORT_MODULE(bindingx)

- (dispatch_queue_t)methodQueue
{
    return RCTGetUIManagerQueue();
}

- (instancetype)init {
    if (self = [super init]) {
        pthread_mutexattr_init(&mutexAttr);
        pthread_mutexattr_settype(&mutexAttr, PTHREAD_MUTEX_RECURSIVE);
        pthread_mutex_init(&mutex, &mutexAttr);
    }
    return self;
}

- (void)dealloc {
    [self unbindAll];
    pthread_mutex_destroy(&mutex);
    pthread_mutexattr_destroy(&mutexAttr);
}

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[BINDING_EVENT_NAME];
}

RCT_EXPORT_METHOD(prepare:(NSDictionary *)dictionary)
{
    [EBUtility setUIManager:self.bridge.uiManager];
    NSString *anchor = dictionary[@"anchor"];
    NSString *eventType = dictionary[@"eventType"];
    
    WXExpressionType exprType = [EBExpressionHandler stringToExprType:eventType];
    if (exprType == WXExpressionTypeUndefined) {
        RCTLogWarn(@"prepare binding eventType error");
        return;
    }
    
    __weak typeof(self) welf = self;
    RCTExecuteOnUIManagerQueue(^{
        // find sourceRef & targetRef
        UIView* sourceComponent = [EBUtility getViewByRef:anchor];
        if (!sourceComponent && (exprType == WXExpressionTypePan || exprType == WXExpressionTypeScroll)) {
            RCTLogWarn(@"prepare binding can't find component");
            return;
        }
        
        pthread_mutex_lock(&mutex);
        
        EBExpressionHandler *handler = [welf handlerForToken:anchor expressionType:exprType];
        if (!handler) {
            // create handler for key
            handler = [EBExpressionHandler handlerWithExpressionType:exprType source:sourceComponent];
            [welf putHandler:handler forToken:anchor expressionType:exprType];
        }
        
        pthread_mutex_unlock(&mutex);
    });
    
}

RCT_EXPORT_SYNCHRONOUS_TYPED_METHOD(NSDictionary *,bind:(NSDictionary *)dictionary)
{
    [EBUtility setUIManager:self.bridge.uiManager];
    if (!dictionary) {
        RCTLogWarn(@"bind params error, need json input");
        return nil;
    }
    
    NSString *eventType =  dictionary[@"eventType"];
    NSArray *props = dictionary[@"props"];
    NSString *token = dictionary[@"anchor"];
    NSDictionary *exitExpression = dictionary[@"exitExpression"];
    NSDictionary *options = dictionary[@"options"];
    
    if ([EBUtility isBlankString:eventType] || !props || props.count == 0) {
        RCTLogWarn(@"bind params error");
        [self sendEventWithName:BINDING_EVENT_NAME body:@{@"state":@"error",@"msg":@"bind params error"}];
        return nil;
    }
    
    WXExpressionType exprType = [EBExpressionHandler stringToExprType:eventType];
    if (exprType == WXExpressionTypeUndefined) {
        RCTLogWarn( @"bind params handler error");
        [self sendEventWithName:BINDING_EVENT_NAME body:@{@"state":@"error",@"msg":@"bind params handler error"}];
        return nil;
    }
    
    if ([token isKindOfClass:NSNumber.class]) {
        token = [(NSNumber *)token stringValue];
    }
    if ([EBUtility isBlankString:token]){
        if ((exprType == WXExpressionTypePan || exprType == WXExpressionTypeScroll)) {
            RCTLogWarn(@"bind params handler error");
            [self sendEventWithName:BINDING_EVENT_NAME body:@{@"state":@"error",@"msg":@"anchor cannot be blank when type is pan or scroll"}];
            return nil;
        } else {
            token = [[NSUUID UUID] UUIDString];
        }
    }
    
    __weak typeof(self) welf = self;
    RCTExecuteOnUIManagerQueue(^{
        
        NSMapTable<id, NSDictionary *> *targetExpression = [NSMapTable new];
        for (NSDictionary *targetDic in props) {
            NSString *targetRef = targetDic[@"element"];
            NSString *property = targetDic[@"property"];
            NSDictionary *expression = targetDic[@"expression"];
            
            if (targetRef) {
                
                NSMutableDictionary *propertyDic = [[targetExpression  objectForKey:targetRef] mutableCopy];
                if (!propertyDic) {
                    propertyDic = [NSMutableDictionary dictionary];
                }
                NSMutableDictionary *expDict = [NSMutableDictionary dictionary];
                expDict[@"expression"] = [self parseExpression:expression];
                if( targetDic[@"config"] )
                {
                    expDict[@"config"] = targetDic[@"config"];
                }
                propertyDic[property] = expDict;
                [targetExpression setObject:propertyDic forKey:targetRef];
            }
        }
        
        // find handler for key
        pthread_mutex_lock(&mutex);
        
        EBExpressionHandler *handler = [welf handlerForToken:token expressionType:exprType];
        if (!handler) {
            // create handler for key
            handler = [EBExpressionHandler handlerWithExpressionType:exprType source:token];
            [welf putHandler:handler forToken:token expressionType:exprType];
        }
        
        [handler updateTargetExpression:targetExpression
                                options:options
                         exitExpression:[self parseExpression:exitExpression]
                               callback:^(id  _Nonnull source, id  _Nonnull result, BOOL keepAlive) {
                                   id body = nil;
                                   if ([result isKindOfClass:NSDictionary.class]) {
                                       body = [result mutableCopy];
                                       [body setObject:source forKey:@"token"];
                                   } else {
                                       body = result;
                                   }
                                   [welf sendEventWithName:BINDING_EVENT_NAME body:body];
                                   if (keepAlive) {
                                       [welf stopObserving];
                                   }
                               }];
        pthread_mutex_unlock(&mutex);
    });
    return @{@"token":token};
}

RCT_EXPORT_METHOD(unbind:(NSDictionary *)options)
{
    if (!options) {
        RCTLogWarn(@"unbind params error, need json input");
        return;
    }
    NSString* token = options[@"token"];
    NSString* eventType = options[@"eventType"];
    
    if ([EBUtility isBlankString:token] || [EBUtility isBlankString:eventType]) {
        RCTLogWarn(@"disableBinding params error");
        return;
    }
    
    WXExpressionType exprType = [EBExpressionHandler stringToExprType:eventType];
    if (exprType == WXExpressionTypeUndefined) {
        RCTLogWarn(@"disableBinding params handler error");
        return;
    }
    
    pthread_mutex_lock(&mutex);
    
    EBExpressionHandler *handler = [self handlerForToken:token expressionType:exprType];
    if (!handler) {
        RCTLogWarn(@"disableBinding can't find handler handler");
        pthread_mutex_unlock(&mutex);
        return;
    }
    
    [handler removeExpressionBinding];
    [self removeHandler:handler forToken:token expressionType:exprType];
    
    pthread_mutex_unlock(&mutex);
}

RCT_EXPORT_METHOD(unbindAll)
{
    pthread_mutex_lock(&mutex);
    
    for (NSString *sourceRef in self.sourceMap) {
        NSMutableDictionary *handlerMap = self.sourceMap[sourceRef];
        for (NSNumber *expressionType in handlerMap) {
            EBExpressionHandler *handler = handlerMap[expressionType];
            [handler removeExpressionBinding];
        }
        [handlerMap removeAllObjects];
    }
    [self.sourceMap removeAllObjects];
    
    pthread_mutex_unlock(&mutex);
}

RCT_EXPORT_SYNCHRONOUS_TYPED_METHOD(NSArray *, supportFeatures)
{
    return @[@"pan",@"scroll",@"orientation",@"timing"];
}

RCT_EXPORT_SYNCHRONOUS_TYPED_METHOD(NSDictionary *, getComputedStyle:(NSString *)sourceRef)
{
    if ([EBUtility isBlankString:sourceRef]) {
        RCTLogWarn(@"getComputedStyle params error");
        return nil;
    }
    
    __block NSMutableDictionary *styles = [NSMutableDictionary new];
    
    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    
    RCTExecuteOnMainQueue(^{
        UIView* view = [EBUtility getViewByRef:sourceRef];
        if (!view) {
            RCTLogWarn(@"source Ref not exist");
        } else {
            CALayer *layer = view.layer;
            styles[@"translateX"] = [self transformFactor:@"transform.translation.x" layer:layer];
            styles[@"translateY"] = [self transformFactor:@"transform.translation.y" layer:layer];
            styles[@"scaleX"] = [self transformFactor:@"transform.scale.x" layer:layer];
            styles[@"scaleY"] = [self transformFactor:@"transform.scale.y" layer:layer];
            styles[@"rotateX"] = [self transformFactor:@"transform.rotation.x" layer:layer];
            styles[@"rotateY"] = [self transformFactor:@"transform.rotation.y" layer:layer];
            styles[@"rotateZ"] = [self transformFactor:@"transform.rotation.z" layer:layer];
            styles[@"opacity"] = [layer valueForKeyPath:@"opacity"];
            
            styles[@"background-color"] = [self colorAsString:view.backgroundColor.CGColor];
        }
        dispatch_semaphore_signal(semaphore);
    });
    
    //color for RCTText
    RCTExecuteOnUIManagerQueue(^{
        RCTShadowView* shadowView = [self.bridge.uiManager shadowViewForReactTag:@([sourceRef integerValue])];
        if ([shadowView isKindOfClass:RCTShadowText.class]) {
            RCTShadowText *shadowText = (RCTShadowText *)shadowView;
            styles[@"color"] = [self colorAsString:shadowText.color.CGColor];
        }
        dispatch_semaphore_signal(semaphore);
    });
    
    dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);
    dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);
    
    return styles;
}

- (NSNumber *)transformFactor:(NSString *)key layer:(CALayer* )layer {
    CGFloat factor = [EBUtility factor];
    id value = [layer valueForKeyPath:key];
    if(value){
        return [NSNumber numberWithDouble:([value doubleValue] / factor)];
    }
    return nil;
}

- (NSString *)colorAsString:(CGColorRef)cgColor
{
    const CGFloat *components = CGColorGetComponents(cgColor);
    if (components) {
        return [NSString stringWithFormat:@"rgba(%d,%d,%d,%f)", (int)(components[0]*255), (int)(components[1]*255), (int)(components[2]*255), components[3]];
    }
    return nil;
}

#pragma mark - Handler Map
- (NSMutableDictionary<NSString *, NSMutableDictionary<NSNumber *, EBExpressionHandler *> *> *)sourceMap {
    if (!_sourceMap) {
        _sourceMap = [NSMutableDictionary<NSString *, NSMutableDictionary<NSNumber *, EBExpressionHandler *> *> dictionary];
    }
    return _sourceMap;
}

- (NSMutableDictionary<NSNumber *, EBExpressionHandler *> *)handlerMapForToken:(NSString *)token {
    return [self.sourceMap objectForKey:token];
}

- (EBExpressionHandler *)handlerForToken:(NSString *)token expressionType:(WXExpressionType)exprType {
    return [[self handlerMapForToken:token] objectForKey:[NSNumber numberWithInteger:exprType]];
}

- (void)putHandler:(EBExpressionHandler *)handler forToken:(NSString *)token expressionType:(WXExpressionType)exprType {
    NSMutableDictionary<NSNumber *, EBExpressionHandler *> *handlerMap = [self handlerMapForToken:token];
    if (!handlerMap) {
        handlerMap = [NSMutableDictionary<NSNumber *, EBExpressionHandler *> dictionary];
        self.sourceMap[token] = handlerMap;
    }
    handlerMap[[NSNumber numberWithInteger:exprType]] = handler;
}

- (void)removeHandler:(EBExpressionHandler *)handler forToken:(NSString *)token expressionType:(WXExpressionType)exprType {
    NSMutableDictionary<NSNumber *, EBExpressionHandler *> *handlerMap = [self handlerMapForToken:token];
    if (handlerMap) {
        [handlerMap removeObjectForKey:[NSNumber numberWithInteger:exprType]];
    }
}

- (id)parseExpression:(NSDictionary *)expression
{
    if ([expression isKindOfClass:NSDictionary.class]) {
        NSString* transformedExpressionStr = expression[@"transformed"];
        if (transformedExpressionStr && [transformedExpressionStr isKindOfClass:NSString.class]) {
            return [NSJSONSerialization JSONObjectWithData:[transformedExpressionStr dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingAllowFragments error:nil];
        }
        else {
            return expression[@"origin"];
        }
    } else if ([expression isKindOfClass:NSString.class]) {
        NSString* expressionStr = (NSString *)expression;
        return [NSJSONSerialization JSONObjectWithData:[expressionStr dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingAllowFragments error:nil];
    }
    return nil;
}

@end
