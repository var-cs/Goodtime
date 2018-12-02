package com.apps.adrcotfas.goodtime.Statistics.Main;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.apps.adrcotfas.goodtime.Database.AppDatabase;
import com.apps.adrcotfas.goodtime.LabelAndColor;
import com.apps.adrcotfas.goodtime.Main.LabelsViewModel;
import com.apps.adrcotfas.goodtime.Main.TimerActivity;
import com.apps.adrcotfas.goodtime.R;
import com.apps.adrcotfas.goodtime.Session;
import com.apps.adrcotfas.goodtime.Statistics.SessionViewModel;
import com.apps.adrcotfas.goodtime.Util.ThemeHelper;
import com.apps.adrcotfas.goodtime.databinding.StatisticsFragmentMainBinding;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import static android.app.Activity.RESULT_OK;
import static com.apps.adrcotfas.goodtime.Statistics.Main.SpinnerProductiveTimeType.DAY_OF_WEEK;
import static com.apps.adrcotfas.goodtime.Statistics.Main.SpinnerProductiveTimeType.HOUR_OF_DAY;
import static com.apps.adrcotfas.goodtime.Statistics.Main.SpinnerStatsType.DURATION;
import static com.apps.adrcotfas.goodtime.Util.StringUtils.formatDateAndTime;
import static com.apps.adrcotfas.goodtime.Util.StringUtils.formatLong;
import static com.apps.adrcotfas.goodtime.Util.StringUtils.formatMinutes;
import static com.apps.adrcotfas.goodtime.Util.StringUtils.toPercentage;

public class StatisticsFragment extends Fragment {

    private static final String TAG = StatisticsFragment.class.getSimpleName();

    //TODO: move to separate file
    private class Stats {
        long today;
        long week;
        long month;
        long total;

        Stats(long today, long week, long month, long total) {
            this.today = today;
            this.week  = week;
            this.month = month;
            this.total = total;
        }
    }

    //TODO: move to separate file and remove duplicate code
    private class StatsView {
        TextView today;
        TextView week;
        TextView month;
        TextView total;

        StatsView(TextView today, TextView week, TextView month, TextView total) {
            this.today = today;
            this.week  = week;
            this.month = month;
            this.total = total;
        }
    }

    private LineChart mChartHistory;
    private BarChart mChartProductiveHours;

    private Spinner mStatsType;
    private Spinner mRangeType;
    private Spinner mProductiveTime;

    private TextView mHeaderOverview;
    private TextView mHeaderHistory;
    private TextView mHeaderProductiveTime;

    private CustomXAxisFormatter mXAxisFormatter;

    private List<LocalDate> xValues = new ArrayList<>();

    private StatsView mOverview;
    private StatsView mOverviewDescription;
    private LabelsViewModel mLabelsViewModel;
    private SessionViewModel mSessionViewModel;
    private float mDisplayDensity = 1;

    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        StatisticsFragmentMainBinding binding = DataBindingUtil.inflate(
                inflater, R.layout.statistics_fragment_main, container, false);
        View view = binding.getRoot();
        mDisplayDensity = getResources().getDisplayMetrics().density;

        setHasOptionsMenu(true);
        mChartHistory = binding.history.chart;
        mChartProductiveHours = binding.productiveHours.barChart;

        mOverview = new StatsView(
                binding.overview.todayValue,
                binding.overview.weekValue,
                binding.overview.monthValue,
                binding.overview.totalValue);

        mOverviewDescription = new StatsView(
                binding.overview.todayDescription,
                binding.overview.weekDescription,
                binding.overview.monthDescription,
                binding.overview.totalDescription
        );

        mStatsType = binding.overview.statsType;
        mRangeType = binding.history.rangeType;
        mProductiveTime = binding.productiveHours.timeType;

        mHeaderOverview = binding.overview.header;
        mHeaderHistory = binding.history.headerHistory;
        mHeaderProductiveTime = binding.productiveHours.headerProductiveTime;

        binding.backupButton.setOnClickListener(view1 -> {
            //TODO: clean-up
            AppDatabase.closeInstance();
            File file = getContext().getDatabasePath("goodtime-db");
            File destinationPath = new File(getContext().getFilesDir(), "tmp");
            File destinationFile = new File(destinationPath, "Goodtime-Backup-" + formatDateAndTime(System.currentTimeMillis()));

            Runnable r = () -> {
                if (file.exists()) {
                    try {
                        copyFile(file, destinationFile);
                        if (destinationFile.exists()) {
                            Uri fileUri = FileProvider.getUriForFile(getContext(), "com.apps.adrcotfas.goodtime", destinationFile);
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_SEND);
                            intent.setType("application/zip");
                            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(intent, "ceva fin"));
                        } else {
                            Toast.makeText(getContext(), "Dataabase invalid", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        // TODO: autogenerated
                        e.printStackTrace();
                    }
                }
            };

            Thread t = new Thread(r);
            t.start();
        });

        binding.importButton.setOnClickListener(view13 -> {
            //TODO: warn the user about loosing the data
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            //TODO: add request code
            startActivityForResult(intent, 123);
        });

        mLabelsViewModel = ViewModelProviders.of(getActivity()).get(LabelsViewModel.class);
        mLabelsViewModel.crtExtendedLabel.observe(this, labelAndColor -> refreshUi());

        mSessionViewModel = ViewModelProviders.of(getActivity()).get(SessionViewModel.class);

        setupSpinners();
        setupHistoryChart();
        setupProductiveTimeChart();
        return view;
    }

    private void refreshStats(List<Session> sessions) {
        final boolean isDurationType = mStatsType.getSelectedItemPosition() == DURATION.ordinal();

        final LocalDate today          = new LocalDate();
        final LocalDate thisWeekStart  = today.dayOfWeek().withMinimumValue();
        final LocalDate thisWeekEnd    = today.dayOfWeek().withMaximumValue();
        final LocalDate thisMonthStart = today.dayOfMonth().withMinimumValue();
        final LocalDate thisMonthEnd   = today.dayOfMonth().withMaximumValue();

        Stats stats = new Stats(0, 0, 0, 0);

        for (Session s : sessions) {
            final Long increment = isDurationType ? s.totalTime : 1L;

            final LocalDate crt = new LocalDate(new Date(s.endTime));
            if (crt.isEqual(today)) {
                stats.today += increment;
            }
            if (crt.isAfter(thisWeekStart.minusDays(1)) && crt.isBefore(thisWeekEnd.plusDays(1))) {
                stats.week += increment;
            }
            if (crt.isAfter(thisMonthStart.minusDays(1)) && crt.isBefore(thisMonthEnd.plusDays(1))) {
                stats.month += increment;
            }
            if (isDurationType) {
                stats.total += increment;
            }
        }
        if (!isDurationType) {
            stats.total += sessions.size();
        }

        mOverview.today.setText(isDurationType
                ? formatMinutes(stats.today)
                : formatLong(stats.today));
        mOverview.week.setText(isDurationType
                ? formatMinutes(stats.week)
                : formatLong(stats.week));
        mOverview.month.setText(isDurationType
                ? formatMinutes(stats.month)
                : formatLong(stats.month));
        mOverview.total.setText(isDurationType
                ? formatMinutes(stats.total)
                : formatLong(stats.total));

        int color = mLabelsViewModel.crtExtendedLabel.getValue().color;
        mOverview.today.setTextColor(color);
        mOverview.week.setTextColor(color);
        mOverview.month.setTextColor(color);
        mOverview.total.setTextColor(color);

        mHeaderOverview.setTextColor(color);
        mHeaderHistory.setTextColor(color);
        mHeaderProductiveTime.setTextColor(color);

        // TODO: extract string resource
        mOverviewDescription.week.setText("Week " + thisWeekStart.getWeekOfWeekyear());
        mOverviewDescription.month.setText(thisMonthEnd.toString("MMMM"));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 123) {
            //TODO: verify if the file is valid, and only after that copy
            //TODO: after the copy is done, call refreshUi to reset the graph
            //TODO: clean-up
            if (resultCode == RESULT_OK) {
                try {
                    InputStream inputStream = getActivity().getContentResolver().openInputStream(data.getData());
                    AppDatabase.destroyInstance();
                    File destinationPath = getContext().getDatabasePath("goodtime-db");
                    //TODO: copy should be done on a background thread
                    copy(inputStream, destinationPath);
                    //TODO: refresh checkboxes (labels were probably changed)
                    refreshUi();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //TODO: move these copy functions to an utility class
    private static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        try (FileChannel source = new FileInputStream(sourceFile).getChannel(); FileChannel destination = new FileOutputStream(destFile).getChannel()) {
            destination.transferFrom(source, 0, source.size());
        }
    }

    public static void copy(InputStream inStream, File dst) throws IOException
    {
        FileOutputStream outStream = new FileOutputStream(dst);
        copy(inStream, outStream);
    }

    public static void copy(InputStream in, OutputStream out) throws IOException
    {
        int numBytes;
        byte[] buffer = new byte[1024];

        while ((numBytes = in.read(buffer)) != -1)
            out.write(buffer, 0, numBytes);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> statsTypeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.spinner_stats_type, R.layout.spinner_item);
        statsTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mStatsType.setAdapter(statsTypeAdapter);
        mStatsType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                refreshUi();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        ArrayAdapter < CharSequence > rangeTypeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.spinner_range_type, R.layout.spinner_item);
        rangeTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mRangeType.setAdapter(rangeTypeAdapter);
        mRangeType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                mXAxisFormatter.setRangeType(SpinnerRangeType.values()[position]);
                refreshUi();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        ArrayAdapter < CharSequence > timeTypeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.spinner_productive_time_type, R.layout.spinner_item);
        timeTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mProductiveTime.setAdapter(timeTypeAdapter);
        mProductiveTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                //TODO: optimize to not load the sessions at every spinner click when not necessary
                refreshUi();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
    }

    private void refreshProductiveTimeChart(List<Session> sessions) {
        // generate according to spinner
        if (mProductiveTime.getSelectedItemPosition() == HOUR_OF_DAY.ordinal()) {
            generateProductiveTimeChart(sessions, HOUR_OF_DAY);

            final int visibleXCount = (int) ThemeHelper.pxToDp(getContext(), mChartHistory.getWidth()) / 36;
            mChartProductiveHours.setVisibleXRangeMaximum(visibleXCount);
            mChartProductiveHours.setVisibleXRangeMinimum(visibleXCount);
            mChartProductiveHours.getXAxis().setLabelCount(visibleXCount);

        } else {
            generateProductiveTimeChart(sessions, DAY_OF_WEEK);

            mChartProductiveHours.setVisibleXRangeMaximum(7);
            mChartProductiveHours.setVisibleXRangeMinimum(7);
            mChartProductiveHours.getXAxis().setLabelCount(7);
        }

        mChartProductiveHours.getBarData().setDrawValues(false);
        IBarDataSet b = mChartProductiveHours.getData().getDataSets().get(0);

        float maxY = 0;
        int maxIdx = 0;
        for (int i = 0; i <  b.getEntryCount(); ++i) {
            float crtY = b.getEntryForIndex(i).getY();
            if (crtY > maxY) {
                maxY = crtY;
                maxIdx = i;
            }
        }

        mChartProductiveHours.moveViewToX(maxIdx);
        mChartProductiveHours.invalidate();
        mChartProductiveHours.notifyDataSetChanged();
    }

    private void refreshUi() {
        //TODO: adapt string when translating
        if (mLabelsViewModel.crtExtendedLabel.getValue() != null) {
            if (mLabelsViewModel.crtExtendedLabel.getValue().label.equals("total")) {
                mSessionViewModel.getAllSessionsByEndTime().observe(this, sessions -> {
                            refreshStats(sessions);
                            refreshHistoryChart(sessions);
                            refreshProductiveTimeChart(sessions);
                        });
            } else if (mLabelsViewModel.crtExtendedLabel.getValue().label.equals("unlabeled")) {
                mSessionViewModel.getAllSessionsUnlabeled().observe(this, sessions -> {
                            refreshStats(sessions);
                            refreshHistoryChart(sessions);
                            refreshProductiveTimeChart(sessions);
                        });
            } else {
                mSessionViewModel.getSessions(mLabelsViewModel.crtExtendedLabel.getValue().label).observe(this, sessions -> {
                            refreshStats(sessions);
                            refreshHistoryChart(sessions);
                            refreshProductiveTimeChart(sessions);
                        });
            }
        }
    }

    private void refreshHistoryChart(List<Session> sessions) {

        final LineData data = generateHistoryChartData(sessions);
        final boolean isDurationType = mStatsType.getSelectedItemPosition() == DURATION.ordinal();

        mChartHistory.moveViewToX(data.getXMax());
        mChartHistory.setData(data);
        mChartHistory.getData().setHighlightEnabled(false);

        mChartHistory.getAxisLeft().setAxisMinimum(0f);
        mChartHistory.getAxisLeft().setAxisMaximum(isDurationType ? 60f : 6f);

        final int visibleXCount = (int) ThemeHelper.pxToDp(getContext(), mChartHistory.getWidth()) / 36;
        mChartHistory.setVisibleXRangeMaximum(visibleXCount);
        mChartHistory.setVisibleXRangeMinimum(visibleXCount);
        mChartHistory.getXAxis().setLabelCount(visibleXCount);
        mChartHistory.getAxisLeft().setLabelCount(5, true);

        if (sessions.size() > 0 && data.getYMax() >= (isDurationType ? 60 : 6f)) {
            mChartHistory.getAxisLeft().setAxisMaximum(isDurationType ? (float) (Math.ceil((double)(data.getYMax() / 20)) * 20) : data.getYMax() + 5);
        }

        // this part is to align the history chart to the productive time chart by setting the same width
        TextPaint p = new TextPaint();
        p.setTextSize(getResources().getDimension(R.dimen.tinyTextSize));
        int widthOfOtherChart = (int) ThemeHelper.pxToDp(getContext(), (int) p.measureText("100%"));
        mChartHistory.getAxisLeft().setMinWidth(widthOfOtherChart);
        mChartHistory.getAxisLeft().setMaxWidth(widthOfOtherChart);

        mChartHistory.notifyDataSetChanged();
    }

    private void setupHistoryChart() {
        mChartHistory.setXAxisRenderer(new CustomXAxisRenderer(
                mChartHistory.getViewPortHandler(),
                mChartHistory.getXAxis(),
                mChartHistory.getTransformer(YAxis.AxisDependency.LEFT)));

        YAxis yAxis = mChartHistory.getAxisLeft();
        yAxis.setValueFormatter(new CustomYAxisFormatter());
        yAxis.setTextColor(getResources().getColor(R.color.grey_500));
        yAxis.setTextSize(getResources().getDimension(R.dimen.tinyTextSize) / mDisplayDensity);
        yAxis.setDrawAxisLine(false);

        XAxis xAxis = mChartHistory.getXAxis();
        xAxis.setTextColor(getResources().getColor(R.color.grey_500));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        final SpinnerRangeType rangeType =
                SpinnerRangeType.values()[mRangeType.getSelectedItemPosition()];

        mXAxisFormatter = new CustomXAxisFormatter(xValues, rangeType);
        xAxis.setValueFormatter(mXAxisFormatter);
        xAxis.setAvoidFirstLastClipping(false);
        xAxis.setTextSize(getResources().getDimension(R.dimen.tinyTextSize) / mDisplayDensity);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);

        xAxis.setYOffset(10f);

        mChartHistory.getAxisLeft().setGridColor(getResources().getColor(R.color.transparent_dark));
        mChartHistory.getAxisLeft().setGridLineWidth(1);

        mChartHistory.setExtraBottomOffset(20f);
        mChartHistory.setExtraLeftOffset(10f);
        mChartHistory.getAxisRight().setEnabled(false);
        mChartHistory.getDescription().setEnabled(false);
        mChartHistory.setNoDataText("");
        mChartHistory.setHardwareAccelerationEnabled(true);
        mChartHistory.animateY(500, Easing.EasingOption.EaseOutCubic);
        mChartHistory.getLegend().setEnabled(false);
        mChartHistory.setDoubleTapToZoomEnabled(false);
        mChartHistory.setScaleEnabled(false);
        mChartHistory.invalidate();
        mChartHistory.notifyDataSetChanged();
    }

    private LineData generateHistoryChartData(List<Session> sessions) {

        final SpinnerStatsType statsType =
                SpinnerStatsType.values()[mStatsType.getSelectedItemPosition()];
        final SpinnerRangeType rangeType =
                SpinnerRangeType.values()[mRangeType.getSelectedItemPosition()];

        final int DUMMY_INTERVAL_RANGE = 15;

        List<Entry> yVals = new ArrayList<>();
        TreeMap<LocalDate, Integer> tree = new TreeMap<>();

        // generate dummy data
        LocalDate dummyEnd = new LocalDate().plusDays(1);
        switch (rangeType) {
            case DAYS:
                LocalDate dummyBegin = dummyEnd.minusDays(DUMMY_INTERVAL_RANGE);
                for (LocalDate i = dummyBegin; i.isBefore(dummyEnd); i = i.plusDays(1)) {
                    tree.put(i, 0);
                }
                break;
            case WEEKS:
                dummyBegin = dummyEnd.minusWeeks(DUMMY_INTERVAL_RANGE).dayOfWeek().withMinimumValue();
                for (LocalDate i = dummyBegin; i.isBefore(dummyEnd); i = i.plusWeeks(1)) {
                    tree.put(i, 0);
                }
                break;
            case MONTHS:
                dummyBegin = dummyEnd.minusMonths(DUMMY_INTERVAL_RANGE);
                for (LocalDate i = dummyBegin; i.isBefore(dummyEnd); i = i.plusMonths(1).dayOfMonth().withMinimumValue()) {
                    tree.put(i, 0);
                }
                break;
        }

        // this is to sum up entries from the same day for visualization
        for (int i = 0; i < sessions.size(); ++i) {
            LocalDate localTime = new LocalDate();
            switch (rangeType) {
                case DAYS:
                    localTime = new LocalDate(new Date(sessions.get(i).endTime));
                    break;
                case WEEKS:
                    localTime = new LocalDate(new Date(sessions.get(i).endTime)).dayOfWeek().withMinimumValue();
                    break;
                case MONTHS:
                    localTime = new LocalDate(new Date(sessions.get(i).endTime)).dayOfMonth().withMinimumValue();
                    break;
            }

            if (!tree.containsKey(localTime)) {
                tree.put(localTime, statsType == DURATION ? sessions.get(i).totalTime : 1);
            } else {
                tree.put(localTime, tree.get(localTime)
                        + (statsType == DURATION ? sessions.get(i).totalTime : 1));
            }
        }

        if (tree.size() > 0) {
            xValues.clear();
            int i = 0;
            LocalDate previousTime = tree.firstKey();

            for (LocalDate crt : tree.keySet()) {
                // visualize intermediate days/weeks/months in case of days without completed sessions
                LocalDate beforeWhat = new LocalDate();
                switch (rangeType) {
                    case DAYS:
                        beforeWhat = crt.minusDays(1);
                        break;
                    case WEEKS:
                        beforeWhat = crt.minusWeeks(1);
                        break;
                    case MONTHS:
                        beforeWhat = crt.minusMonths(1);
                }

                while(previousTime.isBefore(beforeWhat)) {
                    yVals.add(new Entry(i, 0));

                    switch (rangeType) {
                        case DAYS:
                            previousTime = previousTime.plusDays(1);
                            break;
                        case WEEKS:
                            previousTime = previousTime.plusWeeks(1);
                            break;
                        case MONTHS:
                            previousTime = previousTime.plusMonths(1);
                    }
                    xValues.add(previousTime);
                    ++i;
                }
                yVals.add(new Entry(i, tree.get(crt)));
                xValues.add(crt);
                ++i;
                previousTime = crt;
            }
        }
        return new LineData(generateLineDataSet(yVals));
    }

    private LineDataSet generateLineDataSet(List<Entry> entries) {
        LabelAndColor crtLabel = mLabelsViewModel.crtExtendedLabel.getValue();
        LineDataSet set = new LineDataSet(entries, crtLabel.label);
        set.setColor(crtLabel.color);
        set.setCircleColor(crtLabel.color);
        set.setFillColor(crtLabel.color);
        set.setLineWidth(3f);
        set.setCircleRadius(3f);
        set.setDrawCircleHole(false);
        set.disableDashedLine();
        set.setDrawFilled(true);
        set.setDrawValues(false);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            set.setDrawFilled(false);
            set.setLineWidth(2f);
            set.setCircleSize(4f);
            set.setDrawCircleHole(true);
        }
        return set;
    }

    private void setupProductiveTimeChart() {
        YAxis yAxis = mChartProductiveHours.getAxisLeft();
        yAxis.setValueFormatter((value, axis) -> toPercentage(value));
        yAxis.setTextColor(getResources().getColor(R.color.grey_500));
        yAxis.setGranularity(0.25f);
        yAxis.setTextSize(getResources().getDimension(R.dimen.tinyTextSize) / mDisplayDensity);
        yAxis.setAxisMaximum(1.F);
        yAxis.setDrawGridLines(true);
        yAxis.setDrawAxisLine(false);

        XAxis xAxis = mChartProductiveHours.getXAxis();
        xAxis.setTextColor(getResources().getColor(R.color.grey_500));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        xAxis.setAvoidFirstLastClipping(false);
        xAxis.setTextSize(getResources().getDimension(R.dimen.tinyTextSize) / mDisplayDensity);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);

        mChartProductiveHours.setXAxisRenderer(new CustomXAxisRenderer(
                mChartProductiveHours.getViewPortHandler(),
                xAxis,
                mChartProductiveHours.getTransformer(YAxis.AxisDependency.LEFT)));

        mChartProductiveHours.getAxisLeft().setGridColor(getResources().getColor(R.color.transparent_dark));
        mChartProductiveHours.getAxisLeft().setGridLineWidth(1);


        mChartProductiveHours.setExtraBottomOffset(20f);
        mChartProductiveHours.getAxisRight().setEnabled(false);
        mChartProductiveHours.getDescription().setEnabled(false);
        mChartProductiveHours.setNoDataText("");
        mChartProductiveHours.setHardwareAccelerationEnabled(true);
        mChartProductiveHours.animateY(500, Easing.EasingOption.EaseOutCubic);
        mChartProductiveHours.getLegend().setEnabled(false);
        mChartProductiveHours.setDoubleTapToZoomEnabled(false);
        mChartProductiveHours.setScaleEnabled(false);
        mChartProductiveHours.invalidate();
        mChartProductiveHours.notifyDataSetChanged();
    }

    private void generateProductiveTimeChart(List<Session> sessions, SpinnerProductiveTimeType type) {
        ArrayList<BarEntry> yVals = new ArrayList<>();
        if (type == HOUR_OF_DAY) {
            List<Long> sessionsPerHour = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
            // dummy values
            for (int i = 0; i < sessionsPerHour.size(); ++i) {
                yVals.add(new BarEntry(i, 0.F));
            }

            final long nrOfSessions = sessions.size();
            if (nrOfSessions > 0) {
                // hour of day
                for (Session s : sessions) {
                    int crtHourOfDay = new DateTime(s.endTime).getHourOfDay();
                    sessionsPerHour.set(crtHourOfDay, sessionsPerHour.get(crtHourOfDay) + 1);
                }

                for (int i = 0; i < sessionsPerHour.size(); ++i) {
                    yVals.set(i, new BarEntry(i, ((float)sessionsPerHour.get(i) / nrOfSessions)));
                }
            }
        } else if (type == DAY_OF_WEEK){
            List<Long> sessionsPerDay = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L);

            // dummy values
            for (int i = 0; i < sessionsPerDay.size(); ++i) {
                yVals.add(new BarEntry(i, 0.F));
            }

            final long nrOfSessions = sessions.size();
            if (nrOfSessions > 0) {
                // day of week
                for (Session s : sessions) {
                    int crtDayOfWeek = new LocalDate(s.endTime).getDayOfWeek() - 1;
                    sessionsPerDay.set(crtDayOfWeek, sessionsPerDay.get(crtDayOfWeek) + 1);
                }

                for (int i = 0; i < sessionsPerDay.size(); ++i) {
                    yVals.set(i, new BarEntry(i, ((float)sessionsPerDay.get(i) / nrOfSessions)));
                }
            }
        } else {
            Log.wtf(TAG, "Something went wrong in generateProductiveTimeChart");
            return;
        }

        BarDataSet set1 = new BarDataSet(yVals, "");
        set1.setColor(mLabelsViewModel.crtExtendedLabel.getValue().color);
        set1.setHighLightAlpha(0);
        set1.setDrawIcons(false);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);

        BarData data = new BarData(dataSets);
        data.setValueTextSize(10f);
        data.setBarWidth(0.4f);
        mChartProductiveHours.getXAxis().setValueFormatter(null);
        mChartProductiveHours.setData(data);
        mChartProductiveHours.getXAxis().setValueFormatter(new ProductiveTimeXAxisFormatter(type));
        mChartProductiveHours.invalidate();
        mChartProductiveHours.notifyDataSetChanged();
    }
}