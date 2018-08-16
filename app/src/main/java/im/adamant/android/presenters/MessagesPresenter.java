package im.adamant.android.presenters;

import android.util.Log;

import com.arellomobile.mvp.InjectViewState;

import im.adamant.android.Screens;
import im.adamant.android.core.AdamantApi;
import im.adamant.android.core.exceptions.NotAuthorizedException;
import im.adamant.android.interactors.GetContactsInteractor;
import im.adamant.android.interactors.RefreshChatsInteractor;
import im.adamant.android.interactors.SendMessageInteractor;
import im.adamant.android.rx.ChatsStorage;
import im.adamant.android.ui.entities.Chat;
import im.adamant.android.ui.entities.messages.AbstractMessage;
import im.adamant.android.ui.entities.messages.AdamantBasicMessage;
import im.adamant.android.ui.messages_support.SupportedMessageTypes;
import im.adamant.android.ui.mvp_view.MessagesView;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import ru.terrakok.cicerone.Router;

@InjectViewState
public class MessagesPresenter extends BasePresenter<MessagesView>{
    private Router router;
    private SendMessageInteractor sendMessageInteractor;
    private GetContactsInteractor getContactsInteractor;
    private RefreshChatsInteractor refreshChatsInteractor;
    private ChatsStorage chatsStorage;

    private Chat currentChat;
    private List<AbstractMessage> messages;
    private int currentMessageCount = 0;

    private Disposable syncSubscription;

    public MessagesPresenter(
            Router router,
            SendMessageInteractor sendMessageInteractor,
            GetContactsInteractor getContactsInteractor,
            RefreshChatsInteractor refreshChatsInteractor,
            ChatsStorage chatsStorage,
            CompositeDisposable subscriptions
    ) {
        super(subscriptions);
        this.router = router;
        this.sendMessageInteractor = sendMessageInteractor;
        this.getContactsInteractor = getContactsInteractor;
        this.refreshChatsInteractor = refreshChatsInteractor;
        this.chatsStorage = chatsStorage;
    }


    @Override
    public void attachView(MessagesView view) {
        super.attachView(view);

        syncSubscription = refreshChatsInteractor
                .execute()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError((error) -> {
                    if (error instanceof NotAuthorizedException){
                        router.navigateTo(Screens.SPLASH_SCREEN);
                    } else {
                        router.showSystemMessage(error.getMessage());
                    }
                    Log.e("Messages", error.getMessage(), error);
                })
                .doOnComplete(() -> {
                    if (currentMessageCount != messages.size()){
                        getViewState().showChatMessages(messages);
                        currentMessageCount = messages.size();
                    }
                })
                .retryWhen((retryHandler) -> retryHandler.delay(AdamantApi.SYNCHRONIZE_DELAY_SECONDS, TimeUnit.SECONDS))
                .repeatWhen((completed) -> completed.delay(AdamantApi.SYNCHRONIZE_DELAY_SECONDS, TimeUnit.SECONDS))
                .subscribe();

        subscriptions.add(syncSubscription);
    }

    @Override
    public void detachView(MessagesView view) {
        super.detachView(view);
        if (syncSubscription != null){
            syncSubscription.dispose();
        }
    }

    public void onShowChat(Chat chat){
        currentChat = chat;
        messages = chatsStorage.getMessagesByCompanionId(
                chat.getCompanionId()
        );

        getViewState().changeTitle(currentChat.getTitle());

        getViewState()
            .showChatMessages(
                messages
            );
    }

    public void onShowChatByAddress(String address, String label){
        Chat chat = new Chat();
        chat.setCompanionId(address);
        if (label != null && !label.isEmpty()){
            chat.setTitle(label);
        } else {
            chat.setTitle(address);
        }

        onShowChat(chat);
    }

    public void onClickSendMessage(String message){
        //TODO: verify message length and balance

        if (currentChat != null){
            AbstractMessage messageEntity = addUnsendedMessageToChat(message);
            Disposable subscription = sendMessageInteractor
                    .sendMessage(message, currentChat.getCompanionId())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((transaction -> {
                            if (transaction.isSuccess()){
                                messageEntity.setProcessed(true);
                                messageEntity.setTransactionId(transaction.getTransactionId());
                            }

                            getViewState().messageWasSended(messageEntity);
                        }),
                        (error) -> {
                            router.showSystemMessage(error.getMessage());
                            error.printStackTrace();
                        }
                    );

            subscriptions.add(subscription);

            getViewState().goToLastMessage();
        }

    }

    public void onClickShowCompanionDetail() {
        router.navigateTo(Screens.COMPANION_DETAIL_SCREEN, currentChat.getCompanionId());
    }

    private AbstractMessage addUnsendedMessageToChat(String message) {
        AdamantBasicMessage messageEntity = new AdamantBasicMessage();
        messageEntity.setSupportedType(SupportedMessageTypes.ADAMANT_BASIC);
        messageEntity.setiSay(true);
        messageEntity.setText(message);
        messageEntity.setDate(System.currentTimeMillis());
        messageEntity.setProcessed(false);

        //TODO: Encapsulate messages into ChatsStorage.
        if (messages != null){
            messages.add(messageEntity);
        }

        return messageEntity;
    }

}
