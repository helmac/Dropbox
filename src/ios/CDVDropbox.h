#import <Cordova/CDV.h>

@interface CDVDropbox : CDVPlugin

- (void)linkAccount:(CDVInvokedUrlCommand*)command;
- (void)linkedAccounts:(CDVInvokedUrlCommand*)command;
- (void)saveFile:(CDVInvokedUrlCommand*)command;

@end
