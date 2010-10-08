package com.samsung.appengine.allshared;

public final class RemindMeProtocol {
    /**
     * For server messaging (Android C2DM), prevent feedback to the data change originator.
     */
    public static final String ARG_CLIENT_DEVICE_ID = "client_device_id";

    public static final class ServerInfo {
        public static final String METHOD = "server.info";
        public static final String RET_PROTOCOL_VERSION = "protocol_version";
    }

    public static final class UserInfo {
        public static final String METHOD = "user.info";
        public static final String ARG_LOGIN_CONTINUE = "login_continue";
        public static final String RET_USER = "user";
        public static final String RET_LOGIN_URL = "login_url";
        public static final String RET_LOGOUT_URL = "logout_url";
    }

    public static final class AlertsList {
        public static final String METHOD = "alerts.list";
        public static final String RET_NOTES = "alerts";
    }

    public static final class AlertsGet {
        public static final String METHOD = "alerts.get";
        public static final String ARG_ID = "id";
        public static final String RET_ALERT = "alert";
    }

    public static final class AlertsCreate {
        public static final String METHOD = "alerts.create";
        public static final String ARG_ALERT = "alert";
        public static final String RET_ALERT = "alert";
    }

//    public static final class NotesEdit {
//        public static final String METHOD = "notes.edit";
//        public static final String ARG_NOTE = "note";
//        public static final String RET_NOTE = "note";
//    }

    public static final class AlertsDelete {
        public static final String METHOD = "alerts.delete";
        public static final String ARG_ID = "id";
    }

    public static final class AlertsSync {
        public static final String METHOD = "alerts.sync";
        public static final String ARG_SINCE_DATE = "since_date";
        public static final String ARG_LOCAL_ALERTS = "local_alert";
        public static final String RET_ALERTS = "alerts";
        public static final String RET_NEW_SINCE_DATE = "new_since_date";
    }

    public static final class DevicesRegister {
        public static final String METHOD = "devices.register";
        public static final String ARG_DEVICE = "device";
        public static final String RET_DEVICE = "device";
    }

    public static final class DevicesUnregister {
        public static final String METHOD = "devices.unregister";
        public static final String ARG_DEVICE_ID = "device_id";
    }

    public static final class DevicesClear {
        public static final String METHOD = "devices.clear";
    }
}
