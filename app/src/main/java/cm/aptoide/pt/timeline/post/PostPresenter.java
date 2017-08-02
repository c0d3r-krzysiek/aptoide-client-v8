package cm.aptoide.pt.timeline.post;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.analytics.Analytics;
import cm.aptoide.pt.v8engine.crashreports.CrashReport;
import cm.aptoide.pt.presenter.Presenter;
import cm.aptoide.pt.presenter.View;
import cm.aptoide.pt.timeline.post.exceptions.PostException;
import cm.aptoide.pt.view.account.AccountNavigator;
import cm.aptoide.pt.view.app.AppViewFragment;
import cm.aptoide.pt.view.navigator.FragmentNavigator;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import rx.Completable;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

class PostPresenter implements Presenter {
  public static final String UPLOADER_PACKAGENAME = "pt.caixamagica.aptoide.uploader";
  private static final String TAG = PostPresenter.class.getSimpleName();
  private final PostView view;
  private final CrashReport crashReport;
  private final PostManager postManager;
  private final FragmentNavigator fragmentNavigator;
  private final PostFragment.PostUrlProvider postUrlProvider;
  private UrlValidator urlValidator;
  private AccountNavigator accountNavigator;

  public PostPresenter(PostFragment view, CrashReport crashReport, PostManager postManager,
      FragmentNavigator fragmentNavigator, UrlValidator urlValidator,
      AccountNavigator accountNavigator, PostFragment.PostUrlProvider postUrlProvider) {
    this.view = view;
    this.crashReport = crashReport;
    this.postManager = postManager;
    this.fragmentNavigator = fragmentNavigator;
    this.urlValidator = urlValidator;
    this.accountNavigator = accountNavigator;
    this.postUrlProvider = postUrlProvider;
  }

  @Override public void present() {
    if (isExternalOpen()) {
      showPreviewAppsOnStart();
    } else {
      showCardPreviewAfterTextChanges();
      showRelatedAppsAfterTextChanges();
    }
    showRelatedAppsOnStart();
    postOnTimelineOnButtonClick();
    handleCancelButtonClick();
    handleRelatedAppClick();
    onCreateLoginErrorHandle();
    onViewCreatedHandleAppNotFoundErrorAction();
  }

  @Override public void saveState(Bundle state) {
    // does nothing
  }

  @Override public void restoreState(Bundle state) {
    // does nothing
  }

  private void onViewCreatedHandleAppNotFoundErrorAction() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(viewCreated -> view.getAppNotFoundErrorAction())
        .doOnNext(click -> fragmentNavigator.navigateTo(
            AppViewFragment.newInstance(UPLOADER_PACKAGENAME, AppViewFragment.OpenType.OPEN_ONLY)))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(aVoid -> {
        }, throwable -> crashReport.log(throwable));
  }

  private void onCreateLoginErrorHandle() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(viewCreated -> view.getLoginClick())
        .doOnNext(loginClicked -> accountNavigator.navigateToAccountView(
            Analytics.Account.AccountOrigins.POST_ON_TIMELINE))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private void showPreviewAppsOnStart() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.RESUME && isExternalOpen())
        .flatMap(__ -> loadPostPreview(postUrlProvider.getUrlToShare()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private boolean isExternalOpen() {
    return postUrlProvider.getUrlToShare() != null && !postUrlProvider.getUrlToShare()
        .isEmpty();
  }

  private void showRelatedAppsOnStart() {
    view.getLifecycle()
        .filter(event -> getRelatedAppsLifecycleFilter(event))
        .doOnNext(lifecycleEvent -> view.clearAllRelated())
        .doOnNext(lifecycleEvent -> view.showRelatedAppsLoading())
        .observeOn(Schedulers.io())
        .switchMap(viewResumed -> {
          if (isExternalOpen()) {
            return postManager.getSuggestionAppsOnStart(postUrlProvider.getUrlToShare())
                .toObservable();
          } else {
            return postManager.getSuggestionApps()
                .toObservable();
          }
        })
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(relatedApps -> {
          view.addRelatedApps(relatedApps);
          view.hideRelatedAppsLoading();
        })
        .doOnError(throwable -> view.hideRelatedAppsLoading())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private boolean getRelatedAppsLifecycleFilter(View.LifecycleEvent event) {
    return isExternalOpen() ? event.equals(View.LifecycleEvent.RESUME)
        : event.equals(View.LifecycleEvent.CREATE);
  }

  private void handleRelatedAppClick() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(__ -> view.getClickedView()
            .flatMapCompletable(app -> view.setRelatedAppSelected(app)))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private void handleCancelButtonClick() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(__ -> view.cancelButtonPressed()
            .doOnNext(__2 -> goBack()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private void goBack() {
    if (isExternalOpen()) {
      view.exit();
    } else {
      fragmentNavigator.popBackStack();
    }
  }

  private void showCardPreviewAfterTextChanges() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(viewCreated -> view.onInputTextChanged()
            .debounce(1, TimeUnit.SECONDS)
            .map(insertedText -> urlValidator.getUrl(insertedText))
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .switchMap(url -> {
              if (url.isEmpty()) {
                return hidePreview();
              }
              return loadPostPreview(url);
            })
            .doOnError(throwable -> Logger.w(TAG, "showCardPreviewAfterTextChanges: ", throwable)))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> {
          view.hideCardPreviewLoading();
          crashReport.log(err);
        });
  }

  @NonNull private Observable<PostView.PostPreview> loadPostPreview(String url) {
    return Observable.just(url)
        .doOnNext(__2 -> view.showCardPreviewLoading())
        .doOnNext(__2 -> view.hideCardPreview())
        .observeOn(Schedulers.io())
        .flatMapSingle(__2 -> postManager.getPreview(url))
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(suggestion -> view.showCardPreview(suggestion))
        .doOnNext(__2 -> view.hideCardPreviewLoading())
        .doOnError(throwable -> view.hideCardPreviewLoading())
        .onErrorReturn(throwable -> {
          Logger.w(TAG, "showCardPreviewAfterTextChanges: ", throwable);
          view.hideCardPreview();
          view.hideCardPreviewLoading();
          return null;
        });
  }

  @NonNull private Observable<String> hidePreview() {
    return Observable.fromCallable(() -> {
      view.hideCardPreviewTitle();
      view.hideCardPreview();
      view.hideCardPreviewLoading();
      return "";
    });
  }

  private void showRelatedAppsAfterTextChanges() {
    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(viewCreated -> view.onInputTextChanged()
            .debounce(1, TimeUnit.SECONDS)
            .map(insertedText -> urlValidator.getUrl(insertedText))
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(__ -> view.clearRemoteRelated())
            .switchMap(url -> {
              if (url.isEmpty()) {
                view.hideRelatedAppsLoading();
                return Observable.just("");
              }
              return loadRelatedApps(url);
            }))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> crashReport.log(err));
  }

  private Observable<List<PostRemoteAccessor.RelatedApp>> loadRelatedApps(String url) {
    return Completable.fromAction(() -> view.showRelatedAppsLoading())
        .observeOn(Schedulers.io())
        .andThen(postManager.getSuggestionApps(url))
        .observeOn(AndroidSchedulers.mainThread())
        .toObservable()
        .doOnNext(relatedApps -> view.addRelatedApps(relatedApps))
        .doOnCompleted(() -> view.hideRelatedAppsLoading())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnError(throwable -> view.hideRelatedAppsLoading())
        .onErrorReturn(throwable -> Collections.emptyList());
  }

  private void postOnTimelineOnButtonClick() {

    view.getLifecycle()
        .filter(event -> event == View.LifecycleEvent.CREATE)
        .flatMap(__ -> view.shareButtonPressed()
            .observeOn(Schedulers.io())
            .flatMapCompletable(textToShare -> {
              String url;
              url = getUrl(textToShare);
              return postManager.post(url, textToShare, view.getCurrentSelected() == null ? null
                  : view.getCurrentSelected()
                      .getPackageName())
                  .observeOn(AndroidSchedulers.mainThread())
                  .doOnCompleted(() -> view.showSuccessMessage())
                  .doOnCompleted(() -> goBack());
            })
            .doOnError(throwable -> handleError(throwable))
            .retry())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, err -> {
          crashReport.log(err);
          goBack();
        });
  }

  @Nullable private String getUrl(String textToShare) {
    String url;
    if (isExternalOpen()) {
      url = postUrlProvider.getUrlToShare();
    } else {
      url = urlValidator.containsUrl(textToShare) ? urlValidator.getUrl(textToShare) : null;
    }
    return url;
  }

  private void handleError(Throwable throwable) {
    Logger.e(TAG, throwable);
    if (throwable instanceof PostException) {
      switch (((PostException) throwable).getErrorCode()) {
        case INVALID_TEXT:
          view.showInvalidTextError();
          break;
        case INVALID_PACKAGE:
          view.showInvalidPackageError();
          break;
        case NO_LOGIN:
          view.showNoLoginError();
          break;
        case NO_APP_FOUND:
          view.showAppNotFoundError();
          break;
      }
    } else {
      view.showGenericError();
    }
  }
}