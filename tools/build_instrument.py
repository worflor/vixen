"""Build the offline instrument and dependency-free, self-contained SDK exports."""
from pathlib import Path
import ast,html,json,re
ROOT=Path(__file__).resolve().parent.parent
DATA=ROOT/'research';ART=ROOT/'instrument'
def js_body(path):
 s=path.read_text(encoding='utf-8');s=re.sub(r'^import .*?;\s*','',s,flags=re.M)
 s=re.sub(r'^export \{[^\n]+\}(?: from [^\n]+)?;\s*','',s,flags=re.M)
 return re.sub(r'^export ', '',s,flags=re.M)
def js_factory(file,exports):
 names=','.join(exports)
 values=','.join(key+':'+value for key,value in exports.items())
 return 'const {'+names+'}=(()=>{\n'+js_body(ROOT/file)+'\nreturn {'+values+'};})();\n'
modules=[
 ('vixen-api.mjs',{name:name for name in ['registerCurveDecorator', 'decorateCurve', 'registerRootCurve', 'registerUnaryCurve', 'registerMultiCurve', 'evaluateCurve','evaluateCurveJet', 'curveOrigin', 'curveLineage', 'curveConfig', 'curveDomain', 'latestWriteTime', 'curveInfluenceHead', 'appendInfluence', 'materializeInfluences', 'scopeCurveEnclosure', 'ownsCurveEnclosure', 'queryControls','snapshotCurve','forkCurve','restoreCurveGraph','GRAPH_ALGORITHM']}),
 ('vixen-float.mjs',{name:name for name in ['exactMean','sumProducts']}),
 ('vixen-wide.mjs',{'Wide':'Wide','WIDE_FORMAT':'WIDE_FORMAT'}),
 ('vixen-scaled-state.mjs',{name:name for name in ['applyWidePolicy','makeScaledSample']}),
 ('vixen-exponential.mjs',{'wideExp':'wideExp'}),
 ('vixen-timeline.mjs',{'historyAppend':'append','historyBefore':'before','historyEntries':'entries'}),
 ('vixen-checkpoint.mjs',{'historyWitness':'historyWitness'}),
 ('vixen-guide.mjs',{name:name for name in ['polynomialGuide','taylorGuide','sampledGuide','captureGuide','compileGuide','evaluateGuide','guideJet']}),
 ('vixen-calculus.mjs',{name:name for name in ['jet','jetAdd','jetSub','jetMul','jetDiv','jetExp','jetLog','jetTanh','jetSmooth5','jetBoundaryRamp','leafJet','nativeNormalized','applyBoundaryJets']}),
 ('vixen-jet-composition.mjs',{name:name for name in ['blendJets','deriveAdapters','finishJet']}),
 ('vixen-influence-calculus.mjs',{'influenceJet':'influenceJet'}),
 ('vixen-operation-calculus.mjs',{name:name for name in ['operatorJet','coupledJets']}),
 ('vixen-extended-operations.mjs',{name:name for name in ['extendedPolynomialGuide','withExtendedInfluence','withExtendedImpulse','withExtendedRedistribution','restoreExtendedOperation','EXTENDED_OPERATION_ALGORITHM','extendedProxy','extendedPublic']}),
 ('vixen-extended-coupling.mjs',{name:name for name in ['coupleExtended','restoreExtendedCoupling','EXTENDED_COUPLING_ALGORITHM']}),
 ('vixen-extended.mjs',{name:name for name in ['createExtended','restoreExtended','EXTENDED_ALGORITHM','EXTENDED_TAIL_ALGORITHM','MAX_DOMAIN_END']}),
 ('vixen-interval.mjs',{'I':"Object.freeze({interval,add,subtract,multiply,divide,hull,smooth5,boundaryRamp,exp,tanh,nativeDeviationEnclosure,nativeConstraintState,projectConstraintState,constraintDeviation,mixConstraintStates,appliedDeviation})"}),
 ('vixen-correlation.mjs',{'exactCorrelationFactor':'exactCorrelationFactor'}),
 ('vixen-analysis.mjs',{name:name for name in ['nextCrossing', 'makeLipschitzEnclosure', 'calibrateOccupancy', 'validateOccupancy', 'restoreOccupancy', 'validateCorrelationMatrix', 'createCorrelatedChannels', 'measureChannelCorrelation']}),
 ('vixen-influence.mjs',{name:name for name in ['withInfluence','restoreInfluence','isInfluenceAlgorithm','INFLUENCE_ALGORITHM','INFLUENCE_ALGORITHM_V2','INFLUENCE_ALGORITHM_V3']}),
 ('vixen-operations.mjs',{name:name for name in ['withImpulse','withRedistribution','couple','perturbationJet','restoreOperation','OPERATION_ALGORITHM']}),
]
raw=(ROOT/'vixen.mjs').read_text(encoding='utf-8');raw=re.sub(r'^import .*?;\s*','',raw,flags=re.M)
raw=re.sub(r'^export \{[^\n]+\}(?: from [^\n]+)?;\s*','',raw,flags=re.M)
public=['coupleExtended','EXTENDED_COUPLING_ALGORITHM','extendedPolynomialGuide','withExtendedInfluence','withExtendedImpulse','withExtendedRedistribution','restoreExtendedOperation','EXTENDED_OPERATION_ALGORITHM','createExtended','restoreExtended','EXTENDED_ALGORITHM','EXTENDED_TAIL_ALGORITHM','MAX_DOMAIN_END','Wide','WIDE_FORMAT','wideExp','polynomialGuide','taylorGuide','sampledGuide','captureGuide','compileGuide','evaluateGuide','INFLUENCE_ALGORITHM','INFLUENCE_ALGORITHM_V2','INFLUENCE_ALGORITHM_V3','OPERATION_ALGORITHM','GRAPH_ALGORITHM','couple','perturbationJet','nextCrossing','makeLipschitzEnclosure','calibrateOccupancy','validateOccupancy','restoreOccupancy','validateCorrelationMatrix','createCorrelatedChannels','measureChannelCorrelation']
sdk=''.join(js_factory(file,exports) for file,exports in modules)+raw+'\nexport {'+','.join(public)+'};\n'
kernel=re.sub(r'^export \{[^\n]+\};\s*','',sdk,flags=re.M);kernel=re.sub(r'^export ','',kernel,flags=re.M)
def py_body(path):
 s=path.read_text(encoding='utf-8');lines=s.splitlines(keepends=True)
 for node in ast.walk(ast.parse(s)):
  local=isinstance(node,ast.ImportFrom) and (node.module=='vixen' or (node.module or '').startswith('vixen_'))
  local=local or (isinstance(node,ast.Import) and all(n.name.startswith('vixen_') for n in node.names))
  if local:
   for i in range(node.lineno-1,node.end_lineno):lines[i]=''
 return ''.join(lines)
def py_factory(name,body,exports):
 return 'def '+name+'():\n'+''.join((' '+line if line.strip() else '')+'\n' for line in body.splitlines())+' return '+repr(tuple(exports)).replace("'",'')+'\n'+','.join(exports)+(',' if len(exports)==1 else '')+'='+name+'()\n'
py=py_factory('_api_sdk_factory',py_body(ROOT/'vixen_api.py'),['CurveCapabilities', 'register_root_curve', 'curve_origin', 'curve_lineage', 'curve_config', 'curve_domain', 'latest_write_time', 'curve_influence_head', 'append_influence', 'materialize_influences', 'curve_influences', 'register_unary_curve', 'register_multi_curve', 'evaluate_curve','evaluate_curve_jet', 'query_controls','snapshot_curve','fork_curve','restore_curve_graph','GRAPH_ALGORITHM'])
py+=py_factory('_float_sdk_factory',py_body(ROOT/'vixen_float.py'),['exact_mean','sum_products'])
py+=py_factory('_wide_sdk_factory',py_body(ROOT/'vixen_wide.py'),['Wide','WIDE_FORMAT'])
py+=py_factory('_exponential_sdk_factory',py_body(ROOT/'vixen_exponential.py'),['wide_exp'])
py+=py_factory('_timeline_sdk_factory',py_body(ROOT/'vixen_timeline.py')+'\n_history_append=append\n_history_before=before\n_history_entries=entries',['_history_append','_history_before','_history_entries'])
py+=py_factory('_checkpoint_sdk_factory',py_body(ROOT/'vixen_checkpoint.py')+'\n_history_witness=history_witness',['_history_witness'])
py+=py_factory('_guide_sdk_factory',py_body(ROOT/'vixen_guide.py'),['polynomial_guide','taylor_guide','sampled_guide','capture_guide','compile_guide','evaluate_guide','guide_jet'])
py+=py_factory('_calculus_sdk_factory',py_body(ROOT/'vixen_calculus.py'),['Jet','jet','jet_add','jet_sub','jet_mul','jet_div','jet_exp','jet_log','jet_tanh','jet_smooth5','jet_boundary_ramp','leaf_jet','native_normalized','apply_boundary_jets'])
py+=py_factory('_jet_composition_sdk_factory',py_body(ROOT/'vixen_jet_composition.py'),['blend_jets','derive_adapters','finish_jet','field'])
py+=py_factory('_influence_calculus_sdk_factory',py_body(ROOT/'vixen_influence_calculus.py'),['influence_jet'])
py+=py_factory('_operation_calculus_sdk_factory',py_body(ROOT/'vixen_operation_calculus.py'),['operator_jet','coupled_jets','coupling_weight_jet'])
py+=py_factory('_influence_sdk_factory',py_body(ROOT/'vixen_influence.py'),['with_influence','restore_influence','is_influence_algorithm','INFLUENCE_ALGORITHM','INFLUENCE_ALGORITHM_V2','INFLUENCE_ALGORITHM_V3'])
py+=py_factory('_operations_sdk_factory',py_body(ROOT/'vixen_operations.py'),['with_impulse','with_redistribution','couple','restore_operation','OPERATION_ALGORITHM'])
interval_names=['interval','add','subtract','multiply','divide','hull','smooth5','boundaryRamp','exp','tanh','nativeDeviationEnclosure','nativeConstraintState','projectConstraintState','constraintDeviation','mixConstraintStates','appliedDeviation']
interval_body=py_body(ROOT/'vixen_interval.py')+'\nfrom types import SimpleNamespace\n_I=SimpleNamespace('+','.join(n+'='+n for n in interval_names)+')'
py+=py_factory('_interval_sdk_factory',interval_body,['_I'])
py+=py_factory('_correlation_sdk_factory',py_body(ROOT/'vixen_correlation.py'),['exact_correlation_factor'])
py+=py_factory('_analysis_sdk_factory',py_body(ROOT/'vixen_analysis.py'),['next_crossing', 'make_lipschitz_enclosure', 'calibrate_occupancy', 'validate_occupancy', 'restore_occupancy', 'validate_correlation_matrix', 'create_correlated_channels', 'measure_channel_correlation'])
py+=py_factory('_scaled_state_sdk_factory',py_body(ROOT/'vixen_scaled_state.py'),['apply_wide_policy','make_scaled_sample'])
py+=py_factory('_extended_operations_sdk_factory',py_body(ROOT/'vixen_extended_operations.py'),['extended_polynomial_guide','with_extended_influence','with_extended_impulse','with_extended_redistribution','restore_extended_operation','EXTENDED_OPERATION_ALGORITHM','extended_proxy','extended_public'])
py+=py_factory('_extended_coupling_sdk_factory',py_body(ROOT/'vixen_extended_coupling.py'),['couple_extended','restore_extended_coupling','EXTENDED_COUPLING_ALGORITHM'])
py+=py_factory('_extended_sdk_factory','_append=_history_append\n_before=_history_before\n_entries=_history_entries\nhistory_witness=_history_witness\n'+py_body(ROOT/'vixen_extended.py'),['create_extended','restore_extended','EXTENDED_ALGORITHM','EXTENDED_TAIL_ALGORITHM','MAX_DOMAIN_END'])
py+=py_body(ROOT/'vixen.py')
report=json.loads((DATA/'verification-game.json').read_text());assert report['algorithm']=='vixen-hyperfield-c2/4'
report['browser_ui']=json.loads((DATA/'ui-verification.json').read_text())
source=(ART/'template.html').read_text(encoding='utf-8')
parts={'__KERNEL__':kernel,'__UI__':(ART/'ui.js').read_text(encoding='utf-8'),'__SDK__':html.escape(sdk),'__PYTHON__':html.escape(py),'__VERIFICATION__':html.escape(json.dumps(report,indent=2)),'__EVIDENCE__':html.escape((DATA/'evidence.json').read_text()),'__RHYTHM__':html.escape((DATA/'rhythm-audit.json').read_text())}
for token,contents in parts.items():source=source.replace(token,contents)
assert not re.search(r'__(?:KERNEL|UI|SDK|PYTHON|VERIFICATION|EVIDENCE|RHYTHM|PLOT)__',source);assert '[truncated]' not in source
(ART/'vixen.html').write_text(source,encoding='utf-8');(ROOT/'.smoke').mkdir(exist_ok=True)
(ROOT/'.smoke'/'standalone-vixen.mjs').write_text(sdk,encoding='utf-8');(ROOT/'.smoke'/'standalone_vixen.py').write_text(py,encoding='utf-8')
print('Built offline instrument with self-contained SDK exports;',len(source.encode()),'bytes')
