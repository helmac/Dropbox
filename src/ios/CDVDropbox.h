#import <Cordova/CDV.h>

@interface CDVDropbox : CDVPlugin

@property (nonatomic, copy) NSString *callbackId;

- (void)linkAccount:(CDVInvokedUrlCommand*)command;
- (void)didLinkAccount:(BOOL)linked;
- (void)linkedAccounts:(CDVInvokedUrlCommand*)command;
- (void)saveFile:(CDVInvokedUrlCommand*)command;

@end
