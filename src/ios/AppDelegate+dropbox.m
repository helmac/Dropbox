//
//  AppDelegate+dropbox.h
//  Dropbox
//
//  Created by Telerik Inc.
//
//

#import "AppDelegate+dropbox.h"
#import "CDVDropbox.h"
#import <ObjectiveDropboxOfficial/ObjectiveDropboxOfficial.h>
#import <objc/runtime.h>

@implementation AppDelegate (dropbox)

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}


// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    Method original, swizzled;

    original = class_getInstanceMethod(self, @selector(init));
    swizzled = class_getInstanceMethod(self, @selector(swizzled_init));
    method_exchangeImplementations(original, swizzled);
}

- (AppDelegate *)swizzled_init
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(initDropbox:)
                                                 name:@"UIApplicationDidFinishLaunchingNotification" object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self swizzled_init];
}

// This code will be called immediately after application:didFinishLaunchingWithOptions:. We need
// to process notifications in cold-start situations
- (void)initDropbox:(NSNotification *)notification
{
    if (notification){
        NSString *appKey = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"DropboxAppKey"];

        [DBClientsManager setupWithAppKey:appKey];
    }
}

- (BOOL)application:(UIApplication *)app openURL:(NSURL *)url sourceApplication:(NSString *)source annotation:(id)annotation {
    DBOAuthResult *authResult = [DBClientsManager handleRedirectURL:url];
    BOOL didLinkAccount = NO;
    if (authResult != nil) {
        if ([authResult isSuccess]) {
            NSLog(@"Success! User is logged into Dropbox.");
            NSString *accessToken = [[authResult accessToken] accessToken];
            [[NSUserDefaults standardUserDefaults] setObject:accessToken forKey:@"DropboxAuthKey"];
            didLinkAccount = YES;
        } else if ([authResult isCancel]) {
            NSLog(@"Authorization flow was manually canceled by user!");
        } else if ([authResult isError]) {
            NSLog(@"Error: %@", authResult);
        }
    }
    CDVDropbox *dbxPlugin = [self getCommandInstance:@"dropbox"];
    [dbxPlugin didLinkAccount: didLinkAccount];
    return YES;
}

@end
