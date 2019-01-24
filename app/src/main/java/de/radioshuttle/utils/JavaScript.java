/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import com.squareup.duktape.Duktape;

import de.radioshuttle.db.MqttMessage;

public class JavaScript {

    public static synchronized JavaScript getInstance() {
        if (js == null) {
            js = new JavaScript();
        }
        return js;
    }

    /** formatter - interpreter resources are release after call */
    public String formatMsg(String jsBody, MqttMessage m, int notificationType, String accUser, String accMqttServer, String accPushServer) throws Exception {
        String res = null;
        Context context = initFormatter(jsBody, accUser, accPushServer, accMqttServer);
        try {
            res = formatMsg(context, m, notificationType);
        } finally {
            context.close();
        }
        return res;
    }

    public String formatMsg(Context context, MqttMessage message, int notificationType) {
        String result = null;
        if (context instanceof DuktapeContext) {
            result = ((DuktapeContext) context).formatter.formatMsg(
                    String.valueOf(message.getWhen()),
                    message.getTopic(),
                    message.getMsg(),
                    notificationType
            );
        }
        return result;
    }


    public Context initFormatter(String jsBody, String accUser, String accMqttServer, String accPushServer) throws Exception {
        DuktapeContext dc = new DuktapeContext();
        try {
            Duktape duktape = dc.getInterpreter();

            StringBuilder sb = new StringBuilder();
            sb.append("var acc = new Object();\n");
            sb.append("acc.user = ");
            sb.append("'");
            if (accUser != null)
                sb.append(accUser);
            sb.append("';\n");

            sb.append("acc.mqttServer = ");
            sb.append("'");
            if (accMqttServer != null)
                sb.append(accMqttServer);
            sb.append("';\n");

            sb.append("acc.pushServer = ");
            sb.append("'");
            if (accPushServer != null)
                sb.append(accPushServer);
            sb.append("';\n");

            //gloabls
            duktape.evaluate(sb.toString());

            duktape.evaluate(""
                    + "var DuktapeMsgFormatter = { "
                    + "  formatMsg: function(receivedDateMillis, topic, content, notificationType) { "
                    + "  var msg = new Object(); "
                    + "  if (!content) content = ''; "
                    + "  msg.receivedDate = new Date(Number(receivedDateMillis)); "
                    + "  msg.topic = topic; "
                    + "  msg.content = content; "
                    + "  msg.notificationType = notificationType; "
                    + jsBody + "\n"
                    + "  return content;}"
                    + "};");

            dc.formatter = duktape.get("DuktapeMsgFormatter", DuktapeMsgFormatter.class);


        } catch(Exception e) {
            dc.close();
            throw e;
        }
        return dc;
    }


    public void closeFormatter(Context context) {
        if (context != null) {
            context.close();
        }
    }

    public  interface Context<T> {
        T getInterpreter();
        void close();
    }


    /* implementation specific */

    private static class DuktapeContext implements Context<Duktape> {

        public DuktapeContext() {
            duktape  = Duktape.create();
        }

        @Override
        public Duktape getInterpreter() {
            return duktape;
        }

        @Override
        public void close() {
            duktape.close();
        }

        public Duktape duktape;
        public DuktapeMsgFormatter formatter;
    }


    private interface DuktapeMsgFormatter {
        String formatMsg(String receivedDateMillis, String topic, String content, int notificationType);
    }

    private JavaScript() {
    }

    private static JavaScript js;

    private final static String TAG = JavaScript.class.getSimpleName();

    public static long TIMEOUT_MS = 2000;
}
