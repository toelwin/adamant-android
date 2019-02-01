package im.adamant.android.helpers;

import im.adamant.android.core.AdamantApi;
import im.adamant.android.core.AdamantApiWrapper;
import im.adamant.android.core.exceptions.NotFoundPublicKey;
import im.adamant.android.core.responses.PublicKeyResponse;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.exceptions.UndeliverableException;

import java.util.HashMap;

public class NaivePublicKeyStorageImpl implements PublicKeyStorage {
    private HashMap<String, String> publicKeys = new HashMap<>();
    private AdamantApiWrapper api;

    public NaivePublicKeyStorageImpl(AdamantApiWrapper api) {
        this.api = api;
    }

    //TODO: Return Flowable and you may use zip operator.
    @Override
    public String getPublicKey(String address) {
        if (!publicKeys.containsKey(address)){
            try {
                //TODO: InterrupedException. Application Crashed.
                PublicKeyResponse response = api.getPublicKey(address).blockingFirst();
                if (response.isSuccess()){
                    publicKeys.put(address, response.getPublicKey());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        return publicKeys.containsKey(address) ? publicKeys.get(address) : "";
    }

    public Flowable<String> getPublicKeyFlowable(String address) {
        return Flowable.fromCallable(() -> {
                    if (publicKeys.containsKey(address)) {
                        return publicKeys.get(address);
                    } else {
                        throw new NotFoundPublicKey("");
                    }
                })
                .flatMap(value -> {
                    if (value.isEmpty()){
                        return Flowable.just(value);
                    } else {
                        return api
                                .getPublicKey(address)
                                .flatMap((response) -> {
                                    if (response.isSuccess()){
                                        publicKeys.put(address, response.getPublicKey());
                                        return Flowable.just(response.getPublicKey());
                                    } else {
                                        return Flowable.error(new NotFoundPublicKey(response.getError()));
                                    }
                                });
                    }
                });
    }
}
