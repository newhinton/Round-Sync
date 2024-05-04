package ca.pkay.rcloneexplorer.RecyclerViewAdapters;


import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_FRI;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_MON;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_SAT;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_SUN;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_THU;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_TUE;
import static ca.pkay.rcloneexplorer.Items.Trigger.TRIGGER_DAY_WED;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ca.pkay.rcloneexplorer.Activities.TriggerActivity;
import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.Items.Trigger;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Services.TriggerService;
import es.dmoral.toasty.Toasty;

public class TriggerRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

    private List<Trigger> triggers;
    private final Context context;

    private static final int VIEW_TYPE_SCHEDULE = 0;
    private static final int VIEW_TYPE_INTERVAL = 1;

    public TriggerRecyclerViewAdapter(List<Trigger> triggers, Context context) {
        this.triggers = triggers;
        this.context = context;
    }

    @Override
    public int getItemCount() {
        if (triggers == null) {
            return 0;
        }
        return triggers.size();
    }

    @Override
    public int getItemViewType(int position) {
        return triggers.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_SCHEDULE:
                View scheduleView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_trigger_item_schedule, parent, false);
                return new ScheduleViewHolder(scheduleView);
            case VIEW_TYPE_INTERVAL:
                View intervalView = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_trigger_item_interval, parent, false);
                return new IntervalViewHolder(intervalView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
        final Trigger selectedTrigger = triggers.get(position);

        Task task = (new DatabaseHandler(context)).getTask(selectedTrigger.getTriggerTarget());
        String targetTaskTitle = "ERR: NOTFOUND";
        if(task != null){ targetTaskTitle = task.getTitle(); }

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_SCHEDULE:
                ScheduleViewHolder scheduleView = (ScheduleViewHolder) holder;
                scheduleView.mName.setText(selectedTrigger.getTitle());
                scheduleView.mTarget.setText(targetTaskTitle);
                updateStatusIcon(selectedTrigger, scheduleView.mIcon);
                scheduleView.mIcon.setOnClickListener(v -> setIconListener(selectedTrigger, scheduleView.mIcon));
                scheduleView.mOptions.setOnClickListener(v -> showFileMenu(v, selectedTrigger));

                scheduleView.setWeekdays(selectedTrigger);
                scheduleView.setTime(selectedTrigger, this.context);
                break;

            case VIEW_TYPE_INTERVAL:
                IntervalViewHolder intervalView = (IntervalViewHolder) holder;
                intervalView.mName.setText(selectedTrigger.getTitle());
                intervalView.mTarget.setText(targetTaskTitle);
                updateStatusIcon(selectedTrigger, intervalView.mIcon);
                intervalView.mIcon.setOnClickListener(v -> setIconListener(selectedTrigger, intervalView.mIcon));
                intervalView.mOptions.setOnClickListener(v -> showFileMenu(v, selectedTrigger));
                intervalView.setTime(selectedTrigger, context);
                break;
        }
    }

    private void updateStatusIcon(Trigger trigger, ImageButton button){
        Drawable d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_twotone_check_circle_24, null);
        if(!trigger.isEnabled()){
            d =ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_twotone_cancel_24, null);
        }
        button.setImageDrawable(d);
    }

    private void setIconListener(Trigger trigger, ImageButton button){
        DatabaseHandler db = new DatabaseHandler(context);
        trigger.setEnabled(!trigger.isEnabled());
        db.updateTrigger(trigger);
        updateStatusIcon(trigger, button);

        String message = context.getResources().getString(R.string.message_trigger_disabled);
        if(trigger.isEnabled()){
            message = context.getResources().getString(R.string.message_trigger_enabled);
            new TriggerService(context).queueTrigger();
        } else {
            new TriggerService(context).cancelTrigger(trigger.getId());
        }
        Toasty.info(context, message).show();
    }

    public void setList(ArrayList<Trigger> data) {
        triggers=data;
        notifyDataSetChanged();
    }

    private void editTrigger(Trigger trigger){
        Intent intent = new Intent(context, TriggerActivity.class);
        intent.putExtra(TriggerActivity.ID_EXTRA, trigger.getId());
        context.startActivity(intent);
    }

    private void copyTrigger(Trigger trigger){
        trigger.setTitle(trigger.getTitle() + context.getString(R.string.trigger_copy_suffix));
        Trigger newTrigger = (new DatabaseHandler(context)).createTrigger(trigger, false);
        triggers.add(newTrigger);
        notifyItemInserted(triggers.size() - 1);
    }

    public void deleteTrigger(Trigger trigger) {
        new DatabaseHandler(context).deleteTrigger(trigger.getId());
        int index = triggers.indexOf(trigger);
        if (index >= 0) {
            triggers.remove(index);
            notifyItemRemoved(index);
        }
    }

    private void showFileMenu(View view, final Trigger trigger) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.getMenuInflater().inflate(R.menu.trigger_item_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_edit_trigger:
                    editTrigger(trigger);
                    break;
                case R.id.action_copy_trigger:
                    copyTrigger(trigger);
                    break;
                case R.id.action_delete_trigger:
                    deleteTrigger(trigger);
                    break;
                default:
                    return false;
            }
            return true;
        });
        popupMenu.show();
    }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {

        final View mView;
        final ImageButton mIcon;
        final ImageButton mOptions;
        final TextView mName;
        final TextView mTime;
        final TextView mTarget;


        final TextView mon;
        final TextView tue;
        final TextView wed;
        final TextView thu;
        final TextView fri;
        final TextView sat;
        final TextView sun;

        ScheduleViewHolder(View itemView) {
            super(itemView);
            this.mView = itemView;
            this.mIcon = mView.findViewById(R.id.triggerIcon);
            this.mName = mView.findViewById(R.id.triggerName);
            this.mOptions = mView.findViewById(R.id.triggerOptions);
            this.mTarget = mView.findViewById(R.id.trigger_target);
            this.mTime = mView.findViewById(R.id.intervalLabel);

            this.mon = mView.findViewById(R.id.trigger_enabled_mon);
            this.tue = mView.findViewById(R.id.trigger_enabled_tue);
            this.wed = mView.findViewById(R.id.trigger_enabled_wed);
            this.thu = mView.findViewById(R.id.trigger_enabled_thu);
            this.fri = mView.findViewById(R.id.trigger_enabled_fri);
            this.sat = mView.findViewById(R.id.trigger_enabled_sat);
            this.sun = mView.findViewById(R.id.trigger_enabled_sun);
        }


        private void setTextViewValue(TextView view, boolean disabled){
            if(!disabled){
                view.setPaintFlags(view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        public void setWeekdays(Trigger trigger) {
            setTextViewValue(mon, trigger.isEnabledAtDay(TRIGGER_DAY_MON));
            setTextViewValue(tue, trigger.isEnabledAtDay(TRIGGER_DAY_TUE));
            setTextViewValue(wed, trigger.isEnabledAtDay(TRIGGER_DAY_WED));
            setTextViewValue(thu, trigger.isEnabledAtDay(TRIGGER_DAY_THU));
            setTextViewValue(fri, trigger.isEnabledAtDay(TRIGGER_DAY_FRI));
            setTextViewValue(sat, trigger.isEnabledAtDay(TRIGGER_DAY_SAT));
            setTextViewValue(sun, trigger.isEnabledAtDay(TRIGGER_DAY_SUN));
        }

        public void setTime(Trigger trigger, Context context) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, trigger.getTime()/60);
            calendar.set(Calendar.MINUTE, trigger.getTime()%60);

            DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(context);
            mTime.setText(dateFormat.format(new Date(calendar.getTimeInMillis())));
        }

    }

    public static class IntervalViewHolder extends RecyclerView.ViewHolder {

        final View mView;
        final ImageButton mIcon;
        final TextView mName;
        final ImageButton mOptions;
        final TextView mIinterval;
        final TextView mTarget;

        IntervalViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mIcon = mView.findViewById(R.id.triggerIcon);
            mName = mView.findViewById(R.id.triggerName);
            mOptions = mView.findViewById(R.id.triggerOptions);
            mTarget = mView.findViewById(R.id.trigger_target);
            mIinterval = mView.findViewById(R.id.intervalLabel);
        }

        public void setTime(Trigger trigger, Context context) {
            String text;
            switch (trigger.getTime()) {
                case 15:
                    text = context.getString(R.string.trigger_interval_15_min);
                    break;
                case 30:
                    text = context.getString(R.string.trigger_interval_30_min);
                    break;
                case 120:
                    text = context.getString(R.string.trigger_interval_120_min);
                    break;
                case 60:
                default:
                    text = context.getString(R.string.trigger_interval_60_min);
            }
            mIinterval.setText(text);
        }
    }
}
