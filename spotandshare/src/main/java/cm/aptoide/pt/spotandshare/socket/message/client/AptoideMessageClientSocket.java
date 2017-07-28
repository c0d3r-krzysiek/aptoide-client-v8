package cm.aptoide.pt.spotandshare.socket.message.client;

import cm.aptoide.pt.spotandshare.socket.AptoideClientSocket;
import cm.aptoide.pt.spotandshare.socket.entities.AndroidAppInfo;
import cm.aptoide.pt.spotandshare.socket.entities.Friend;
import cm.aptoide.pt.spotandshare.socket.entities.Host;
import cm.aptoide.pt.spotandshare.socket.exception.ServerLeftException;
import cm.aptoide.pt.spotandshare.socket.interfaces.OnError;
import cm.aptoide.pt.spotandshare.socket.interfaces.SocketBinder;
import cm.aptoide.pt.spotandshare.socket.interfaces.TransferLifecycleProvider;
import cm.aptoide.pt.spotandshare.socket.message.FriendsManager;
import cm.aptoide.pt.spotandshare.socket.message.Message;
import cm.aptoide.pt.spotandshare.socket.message.interfaces.AndroidAppInfoAccepter;
import cm.aptoide.pt.spotandshare.socket.message.interfaces.StorageCapacity;
import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import rx.Observable;

/**
 * Created by neuro on 29-01-2017.
 */

public class AptoideMessageClientSocket extends AptoideClientSocket {

  protected final AptoideMessageClientController aptoideMessageController;

  private final FriendsManager friendsManager;

  public Observable<Collection<Friend>> observeFriends() {
    return friendsManager.observe();
  }

  public AptoideMessageClientSocket(String host, int port, String rootDir,
      StorageCapacity storageCapacity, TransferLifecycleProvider<AndroidAppInfo> fileLifecycleProvider,
      SocketBinder socketBinder, OnError<IOException> onError, int timeout,
      AndroidAppInfoAccepter androidAppInfoAccepter, Friend friend) {
    super(host, port, timeout);
    this.aptoideMessageController =
        new AptoideMessageClientController(this, rootDir, storageCapacity, fileLifecycleProvider,
            socketBinder, onError, androidAppInfoAccepter, friend);
    this.onError = onError;
    this.friendsManager = new FriendsManager();
  }

  public AptoideMessageClientSocket(String host, String fallbackHostName, int port, String rootDir,
      StorageCapacity storageCapacity, TransferLifecycleProvider<AndroidAppInfo> fileLifecycleProvider,
      SocketBinder socketBinder, OnError<IOException> onError, int timeout,
      AndroidAppInfoAccepter androidAppInfoAccepter, Friend friend) {
    super(host, fallbackHostName, port, timeout);
    this.aptoideMessageController =
        new AptoideMessageClientController(this, rootDir, storageCapacity, fileLifecycleProvider,
            socketBinder, onError, androidAppInfoAccepter, friend);
    this.onError = onError;
    this.friendsManager = new FriendsManager();
  }

  @Override public void shutdown() {
    aptoideMessageController.disable();
    super.shutdown();
  }

  @Override protected void onConnected(Socket socket) throws IOException {
    aptoideMessageController.onConnect(socket);
  }

  public Host getHost() {
    return aptoideMessageController.getHost();
  }

  public Host getLocalhost() {
    return aptoideMessageController.getLocalhost();
  }

  public void onConnect(Socket socket) throws IOException {
    aptoideMessageController.onConnect(socket);
  }

  public boolean sendWithAck(Message message) throws InterruptedException {
    return aptoideMessageController.sendWithAck(message);
  }

  public void exit() {
    disable();
    aptoideMessageController.exit();
  }

  public void disable() {
    aptoideMessageController.disable();
    onError = null;
  }

  public boolean isEnabled() {
    return aptoideMessageController.isEnabled();
  }

  public void send(Message message) {
    aptoideMessageController.send(message);
  }

  public void serverLeft() {
    System.out.println("serverLeft called");
    if (onError != null) {
      onError.onError(new ServerLeftException("Server Left"));
    }
    disable();
  }

  public void onNewFriend(Friend friend, Host host) {
    friendsManager.addFriend(friend, host);
  }

  public void onFriendLeft(Host hostThatLeft) {
    friendsManager.removeFriend(hostThatLeft);
  }
}