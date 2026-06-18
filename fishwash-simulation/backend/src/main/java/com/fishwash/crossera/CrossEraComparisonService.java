package com.fishwash.crossera;

import com.fishwash.config.FishWashProperties;
import com.fishwash.dto.CrossEraComparisonResult;
import com.fishwash.entity.FishWashDevice;
import com.fishwash.entity.VibrationMode;
import com.fishwash.repository.FishWashDeviceRepository;
import com.fishwash.repository.VibrationModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CrossEraComparisonService {

    private final FishWashDeviceRepository fishWashDeviceRepository;
    private final VibrationModeRepository vibrationModeRepository;
    private final FishWashProperties fishWashProperties;

    public CrossEraComparisonResult compareAncientVsUltrasonic(Integer ancientDeviceId) {
        FishWashDevice ancient = fishWashDeviceRepository.findById(ancientDeviceId)
                .orElse(fishWashDeviceRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("No fish wash device available")));

        List<VibrationMode> ancientModes = vibrationModeRepository
                .findByDeviceIdOrderByModeOrderAsc(ancient.getId());

        VibrationMode dominantMode = ancientModes.stream()
                .filter(m -> m.getModeOrder() == 2 || m.getModeOrder() == 3)
                .findFirst()
                .orElse(ancientModes.stream().findFirst().orElse(null));

        double ancientFreq = ancient.getBaselineResonanceFreq() != null
                ? ancient.getBaselineResonanceFreq()
                : (dominantMode != null ? dominantMode.getResonanceFreq() : 280.0);
        double ancientSprayCm = ancient.getBaselineSprayHeight() != null
                ? ancient.getBaselineSprayHeight()
                : 15.0;

        FishWashProperties.UltrasonicProps us = fishWashProperties.getUltrasonic();
        FishWashProperties.FluidProps fluid = fishWashProperties.getFluid();

        double usFreq = us.getDefaultFrequency();
        double usPower = us.getDefaultPowerW();
        double usParticleUm = us.getDefaultParticleSizeMicrons();
        double usEfficiency = us.getNebulizationEfficiency();

        double ancientParticleCm = Math.max(0.05, ancientSprayCm * 0.008) * 10000.0;
        double ancientParticleUm = Math.min(5000.0, ancientParticleCm * 1000.0);

        double usFlowMlMin = us.getTypicalOutputRateMlH() / 60.0;
        double ancientFlowMlMin = Math.max(1.0, ancientSprayCm * 0.45);

        double ancientEnergyPerMl = ancientFreq * 0.00042 / Math.max(0.1, ancientFlowMlMin);
        double modernEnergyPerMl = (usPower * 1.0) / Math.max(0.1, usFlowMlMin);
        double effRatio = ancientEnergyPerMl / Math.max(1e-6, modernEnergyPerMl);

        CrossEraComparisonResult.DeviceProfile ancientProfile = new CrossEraComparisonResult.DeviceProfile();
        ancientProfile.setName(ancient.getDeviceName() != null ? ancient.getDeviceName() : "古代青铜鱼洗");
        ancientProfile.setEra(ancient.getEra() != null ? ancient.getEra() : "汉代-清代");
        ancientProfile.setWorkingPrinciple(
                "湿手摩擦双耳产生切向力→经双耳支点传递机械激励→激发圆形薄壳n节径振动模式" +
                "→ALE流固耦合→形成2n个驻波波腹→波腹处水体切向速度最大→当离心加速度>g时抛射喷水");
        ancientProfile.setFrequencyHz(ancientFreq);
        ancientProfile.setParticleSizeMicrons(ancientParticleUm);
        ancientProfile.setEnergyInputW(8.5);
        ancientProfile.setWaterSprayHeightCm(ancientSprayCm);
        ancientProfile.setWaterFlowRateMlMin(ancientFlowMlMin);
        ancientProfile.setActivationMethod("人体双手以湿毛巾/手掌在鱼洗双耳柄沿轴线方向反复摩擦（粘滑激发）");
        ancientProfile.setHistoricalSignificance(
                "汉代青铜器工艺巅峰；中国古代声学、力学与流体力学的综合演示装置；" +
                "现代博物馆中国古代科技展必备展品；比西方类似振动演示早约1800年");
        ancientProfile.setMaterial("青铜(Cu-Sn合金，Sn 12-15%)");

        CrossEraComparisonResult.DeviceProfile modernProfile = new CrossEraComparisonResult.DeviceProfile();
        modernProfile.setName("现代家用压电陶瓷超声波雾化器");
        modernProfile.setEra("1970s-至今 (现代消费电子)");
        modernProfile.setWorkingPrinciple(
                "【行业标准】" + us.getWorkingPrinciple() + "；标准依据：GB/T 35515-2017、IEC 60335-2-98:2018" +
                "；压电换能器以" + String.format(Locale.US, "%.0f", usFreq / 1.0e6) + "MHz频率振动→雾化片表面水膜受高频剪切→Rayleigh波不稳→形成微米级气雾");
        modernProfile.setFrequencyHz(usFreq);
        modernProfile.setParticleSizeMicrons(usParticleUm);
        modernProfile.setEnergyInputW(usPower);
        modernProfile.setWaterSprayHeightCm(0.1);
        modernProfile.setWaterFlowRateMlMin(usFlowMlMin);
        modernProfile.setActivationMethod(
                "市电220V/5V直流→振荡器驱动压电陶瓷换能器→机械阻抗匹配→高频振动传至水-金属界面");
        modernProfile.setHistoricalSignificance(
                "现代消费医疗电子的典型产物；广泛用于加湿器、呼吸道雾化治疗、芳香疗法、工业加湿；" +
                "核心为逆压电效应发现(1880年居里兄弟)+压电陶瓷研发(1950s)");
        modernProfile.setMaterial(
                "PZT-4或PZT-5H锆钛酸铅压电陶瓷+不锈钢雾化片+ABS工程塑料");

        List<CrossEraComparisonResult.RadarDataPoint> radar = new ArrayList<>();

        double logFreqMax = Math.log10(usFreq);
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "振动频率", "frequency",
                Math.log10(ancientFreq) / logFreqMax * 100.0,
                100.0,
                "对数刻度（现代MHz级显著更高）"
        ));
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "喷水/雾化高度", "height",
                Math.min(100.0, ancientSprayCm * 5.0),
                2.0,
                "古代鱼洗可达数十厘米，现代雾化器贴水膜表面"
        ));
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "粒子直径", "particleSize",
                Math.min(100.0, ancientParticleUm / 50.0),
                usParticleUm / 50.0 * 100.0,
                "古代鱼洗为毫米-厘米级水滴；现代雾化器为1-5μm气雾粒"
        ));
        double ancientEffNorm = Math.min(100.0, 1.0 / Math.max(0.001, ancientEnergyPerMl) * 5.0);
        double modernEffNorm = Math.min(100.0, 1.0 / Math.max(0.001, modernEnergyPerMl) * 5.0);
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "能量效率(ml/J)", "energyEfficiency",
                ancientEffNorm,
                modernEffNorm,
                "雾化毫升数/焦耳输入；现代雾化器效率显著更高"
        ));
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "工艺复杂度", "complexity",
                95.0,
                65.0,
                "古代青铜范铸工艺难度极高；现代为标准化电子制造"
        ));
        radar.add(new CrossEraComparisonResult.RadarDataPoint(
                "文化历史价值", "culturalValue",
                100.0,
                25.0,
                "古代鱼洗为文物级展品；现代雾化器为快消日用品"
        ));

        String eraInterpretation = String.format(Locale.US,
                "跨越约2200年的振动技术对比：汉代青铜鱼洗(≈公元前200年)以人体摩擦粘滑效应产生%.1fHz的" +
                "低频机械共振，可将水喷射至%.1fcm的可见高度，是机械振动+流固耦合+ALE网格技术的艺术表达；" +
                "而现代压电雾化器以%.0fMHz高频（差幅约4个数量级）在水-陶瓷界面产生声学空化，" +
                "雾化粒子仅约%.1fμm，用于呼吸道加湿或医疗。两者体现了\"宏观可见的美\"与\"微观高效的用\"两种振动哲学。",
                ancientFreq, ancientSprayCm, usFreq / 1.0e6, usParticleUm);

        String paradigm =
                "振动范式根本差异：\n" +
                "  ① 激励源：鱼洗=人体生物力学粘滑摩擦(低Q值宽频激励源)  vs  雾化器=压电换能器电致伸缩(高Q窄带纯音)\n" +
                "  ② 频率范围：鱼洗100-800Hz(可闻声频)  vs  雾化器1.0-3.0MHz(远超超声)\n" +
                "  ③ 液-固作用机理：鱼洗=边界元+ALE流固耦合(厘米级驻波)  vs  雾化器=声表面波Rayleigh不稳+空化(微米级剪切)\n" +
                "  ④ 能量传导链：鱼洗 手-柄-壳-水-空气(长链路衰减)  vs  雾化器 压电片-水膜-气溶胶(短链路高效)\n" +
                "  ⑤ 产物尺度：鱼洗毫米-厘米级可抛射水滴(能溅湿衣衫)  vs  雾化器1-5μm级悬浮气雾(可吸入肺部)\n" +
                "  ⑥ 设计哲学：鱼洗追求\"共振-美感-教育\"三位一体  vs  雾化器追求\"单位时间雾化量最大化\"";

        CrossEraComparisonResult.EnergyEfficiencyComparison eff =
                new CrossEraComparisonResult.EnergyEfficiencyComparison();
        eff.setAncientJoulesPerMl(ancientEnergyPerMl);
        eff.setModernJoulesPerMl(modernEnergyPerMl);
        eff.setEfficiencyRatio(effRatio);
        eff.setInterpretation(String.format(Locale.US,
                "能量效率比=古代:现代=1:%.2f。现代雾化器每J能量可雾化更多水量(现代设计目标)，" +
                "但古代鱼洗以极低输入(约8.5W体力功耗)即可将宏观水团喷射至可观高度，" +
                "其共振放大倍率可达%.1f倍机械Q值，体现了古典力学利用的高度智慧。",
                1.0 / Math.max(1e-3, effRatio),
                dominantMode != null ? 1.0 / Math.max(0.001, dominantMode.getDampingRatio()) : 59.0));

        CrossEraComparisonResult result = new CrossEraComparisonResult();
        result.setAncientFishWash(ancientProfile);
        result.setModernUltrasonic(modernProfile);
        result.setRadarComparison(radar);
        result.setEraInterpretation(eraInterpretation);
        result.setVibrationParadigmDifference(paradigm);
        result.setEnergyEfficiency(eff);

        return result;
    }
}
