/********* CDVDropbox.m Cordova Plugin Implementation *******/

#import "CDVDropbox.h"
#import <ObjectiveDropboxOfficial/ObjectiveDropboxOfficial.h>

static NSString *accessTokenKey = @"DropboxTelerikAccessToken";
CDVInvokedUrlCommand* onProgressCommand = nil;

@implementation CDVDropbox

@synthesize callbackId;

- (void)pluginInitialize
{
    [super pluginInitialize];
}

- (void)linkedAccounts:(CDVInvokedUrlCommand*)command
{
    NSString *accessToken = [[NSUserDefaults standardUserDefaults] objectForKey:@"DropboxAuthKey"];
    NSArray *mutableArray = @[accessToken];
    
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray: mutableArray];
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    });
}

- (void)linkAccount:(CDVInvokedUrlCommand*)command
{
    NSString *accessToken = [[NSUserDefaults standardUserDefaults] objectForKey:@"DropboxAuthKey"];

    self.callbackId = command.callbackId;

    if (accessToken == nil) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [pluginResult setKeepCallback:nil];
        UIViewController *rootViewController = [[[[UIApplication sharedApplication] delegate] window] rootViewController];
        [DBClientsManager authorizeFromController:[UIApplication sharedApplication]
                                       controller:rootViewController
                                          openURL:^(NSURL *url) {
                                              [[UIApplication sharedApplication] openURL:url];
                                          }];
    } else {
        [self didLinkAccount:YES];
    }
}

- (void)didLinkAccount:(BOOL)linked
{
  if (self.callbackId != nil)
  {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[NSDictionary dictionaryWithObjectsAndKeys:@(linked), @"success", nil]];

    dispatch_async(dispatch_get_main_queue(), ^{
      [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    });
  }
}

- (void)uploadFile:(NSString*)filePath uploadFolder:(NSString*)uploadFilePath saveFileCommand:(CDVInvokedUrlCommand*)command {
    NSString *accessToken = [[NSUserDefaults standardUserDefaults] objectForKey:@"DropboxAuthKey"];
    DBUserClient *client;
    
    if (accessToken != nil) {
        client = [[DBUserClient alloc] initWithAccessToken:accessToken];
    } else {
        client = [DBClientsManager authorizedClient];
    }
    
    NSMutableDictionary<NSURL *, DBFILESCommitInfo *> *uploadFilesUrlsToCommitInfo = [NSMutableDictionary new];
    DBFILESCommitInfo *commitInfo = [[DBFILESCommitInfo alloc] initWithPath:uploadFilePath];
    [uploadFilesUrlsToCommitInfo setObject:commitInfo forKey:[NSURL fileURLWithPath:filePath]];
    
    [client.filesRoutes batchUploadFiles:uploadFilesUrlsToCommitInfo
                                   queue:nil
                           progressBlock:^(int64_t uploaded, int64_t uploadedTotal, int64_t expectedToUploadTotal) {
                               NSLog(@"Uploaded: %lld  UploadedTotal: %lld  ExpectedToUploadTotal: %lld", uploaded, uploadedTotal,
                                     expectedToUploadTotal);
                               CDVPluginResult* pluginResult = nil;

                               pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[NSDictionary dictionaryWithObjectsAndKeys:@(1), @"attempt", @(uploadedTotal), @"uploaded", @(expectedToUploadTotal), @"size", nil]];
                               
                               [pluginResult setKeepCallbackAsBool:YES];
                               
                               dispatch_async(dispatch_get_main_queue(), ^{
                                   [self.commandDelegate sendPluginResult:pluginResult callbackId:onProgressCommand.callbackId];
                               });
                           }
                           responseBlock:^(NSDictionary<NSURL *, DBFILESUploadSessionFinishBatchResultEntry *> *fileUrlsToBatchResultEntries,
                                           DBASYNCPollError *finishBatchRouteError, DBRequestError *finishBatchRequestError,
                                           NSDictionary<NSURL *, DBRequestError *> *fileUrlsToRequestErrors) {
                               CDVPluginResult* pluginResult = nil;

                               if (fileUrlsToBatchResultEntries) {
                                   NSLog(@"Call to `/upload_session/finish_batch/check` succeeded");
                                   for (NSURL *clientSideFileUrl in fileUrlsToBatchResultEntries) {
                                       DBFILESUploadSessionFinishBatchResultEntry *resultEntry = fileUrlsToBatchResultEntries[clientSideFileUrl];
                                       if ([resultEntry isSuccess]) {
                                           NSString *dropboxFilePath = resultEntry.success.pathDisplay;
                                           NSLog(@"File successfully uploaded from %@ on local machine to %@ in Dropbox.",
                                                 [clientSideFileUrl path], dropboxFilePath);
                                            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:[NSDictionary dictionaryWithObjectsAndKeys:@(YES), @"success", nil]];
                                       } else if ([resultEntry isFailure]) {
                                           // This particular file was not uploaded successfully, although the other
                                           // files may have been uploaded successfully. Perhaps implement some retry
                                           // logic here based on `uploadNetworkError` or `uploadSessionFinishError`
//                                           DBRequestError *uploadNetworkError = fileUrlsToRequestErrors[clientSideFileUrl];
//                                           DBFILESUploadSessionFinishError *uploadSessionFinishError = resultEntry.failure;
// TODO
                                           // implement appropriate retry logic
                                       }
                                   }
                               }
                               
                               if (finishBatchRouteError) {
                                   NSLog(@"Either bug in SDK code, or transient error on Dropbox server");
                                   NSLog(@"%@", finishBatchRouteError);
                                   pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"%@", finishBatchRouteError]];
                               } else if (finishBatchRequestError) {
                                   NSLog(@"Request error from calling `/upload_session/finish_batch/check`");
                                   NSLog(@"%@", finishBatchRequestError);
                                   pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"%@", finishBatchRequestError]];
                               } else if ([fileUrlsToRequestErrors count] > 0) {
                                   NSLog(@"Other additional errors (e.g. file doesn't exist client-side, etc.).");
                                   NSLog(@"%@", fileUrlsToRequestErrors);
                                   pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[NSString stringWithFormat:@"%@", fileUrlsToRequestErrors]];
                               }
                               
                               dispatch_async(dispatch_get_main_queue(), ^{
                                   [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                               });
                           }];
}

- (void)saveFile:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;

    NSDictionary* body = [command.arguments objectAtIndex:0];

    NSArray *paths = [body objectForKey:@"files"];
    NSString *folder = [body objectForKey:@"folder"];

    for (NSString *path in paths){
        // normalize
        NSString *relativePath = [path stringByReplacingOccurrencesOfString:@"file://" withString:@""];

        NSString *fileName = [relativePath lastPathComponent];
        
        NSString *uploadPath = [NSString pathWithComponents:[NSArray arrayWithObjects:@"/",folder,fileName, nil]];
        
        @try {
            [self uploadFile:relativePath uploadFolder:uploadPath saveFileCommand:command];
        }
        @catch (NSException * error) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.reason];

            dispatch_async(dispatch_get_main_queue(), ^{
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            });
        }
    }
}

- (void)onProgressUpload:(CDVInvokedUrlCommand*)command
{
    onProgressCommand = command;
}

@end
