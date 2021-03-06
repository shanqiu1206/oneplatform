package com.oneplatform.system.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jeesuite.common.JeesuiteBaseException;
import com.jeesuite.common.util.BeanCopyUtils;
import com.oneplatform.base.exception.AssertUtil;
import com.oneplatform.base.exception.ExceptionCode;
import com.oneplatform.base.model.TreeModel;
import com.oneplatform.system.constants.ResourceType;
import com.oneplatform.system.dao.entity.ModuleEntity;
import com.oneplatform.system.dao.entity.ResourceEntity;
import com.oneplatform.system.dao.entity.RoleEntity;
import com.oneplatform.system.dao.mapper.ModuleEntityMapper;
import com.oneplatform.system.dao.mapper.ResourceEntityMapper;
import com.oneplatform.system.dao.mapper.RoleEntityMapper;
import com.oneplatform.system.dto.ModuleRoleResource;
import com.oneplatform.system.dto.RoleResource;
import com.oneplatform.system.dto.param.ResourceParam;

/**
 * 
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2018年3月24日
 */
@Service
public class ResourcesService {

	private @Autowired ResourceEntityMapper resourceMapper;
	
	private @Autowired ModuleService moduleService;
	
	private @Autowired RoleEntityMapper roleMapper;
	private @Autowired ModuleEntityMapper moduleMapper;
	
	public List<RoleEntity> findRoleWithResourceByUserId(Integer accountId){
		List<RoleEntity> roles = roleMapper.findUserRoles(accountId);
		List<ResourceEntity> resources;
		for (RoleEntity role : roles) {
			resources = resourceMapper.findRoleResources(role.getId(), ResourceType.uri.name());
			role.setResources(resources);
		}
		return roles;
	}
	
	public Set<String> findAllPermsByUserId(Integer accountId){
		
		Set<String> result = new  java.util.HashSet<>();
		List<RoleEntity> roles = roleMapper.findUserRoles(accountId);
		List<ResourceEntity> resources;
		for (RoleEntity role : roles) {
			resources = resourceMapper.findRoleResources(role.getId(), ResourceType.uri.name());
			for (ResourceEntity resourceEntity : resources) {
				result.add(resourceEntity.getCode());
			}
		}
		return result;
	}
	
	public List<ResourceEntity> findResourceByRole(Integer roleId){
		List<ResourceEntity> resources = resourceMapper.findRoleResources(roleId, ResourceType.all.name());
		return resources;
	}
	
	public List<TreeModel> findAllResourceTreeByType(String type,boolean containLeaf){
		List<ResourceEntity> resources;
		if(containLeaf){
			resources = resourceMapper.findResources(type);
		}else{
			resources = resourceMapper.findNotLeafResources(type);
		}
		return buildResourceTree(resources);
	}

	public List<TreeModel> findAllPermissions(){
		List<ResourceEntity> resources = resourceMapper.findAllNotMenuResources();
		//
		//把模块当着父级节点
        for (ResourceEntity resource : resources) {
        	resource.setParentId(resource.getModuleId());
		}
		List<ModuleEntity> modules = moduleMapper.findAllEnabled();
		ResourceEntity resource;
		for (ModuleEntity module : modules) {
			resource = new ResourceEntity();
			resource.setId(module.getId());
			resource.setModuleId(module.getId());
			resource.setName(module.getName());
			resources.add(resource);
		}
		
		return buildResourceTree(resources);
	}
	
	public List<ModuleRoleResource> findAllModuleRoleResources(int roleId){
		Map<Integer, ModuleRoleResource> moduleRoleResources = new HashMap<>();
		//全部模块
		Map<Integer, ModuleEntity> modules = moduleService.getAllModules(true);
		//全部资源
		List<ResourceEntity> resources = resourceMapper.findLeafResources(ResourceType.all.name());
		//角色已分配的资源
		List<ResourceEntity> roleResources = resourceMapper.findRoleResources(roleId, ResourceType.all.name());
		List<Integer> roleResourceIds = new ArrayList<>();
		for (ResourceEntity resource : roleResources) {
		   roleResourceIds.add(resource.getId());
		}
		
		ModuleRoleResource moduleRoleResource;
		ModuleEntity module;
		RoleResource roleResource;
		for (ResourceEntity resource : resources) {
			moduleRoleResource = moduleRoleResources.get(resource.getModuleId());
			if(moduleRoleResource == null){
				module = modules.get(resource.getModuleId());
				if(module == null)continue;
				moduleRoleResource = new ModuleRoleResource(module.getId(), module.getName());
				moduleRoleResources.put(moduleRoleResource.getModuleId(), moduleRoleResource);
			}
			roleResource = new RoleResource(resource.getId(), resource.getModuleId(), resource.getName(), resource.getType());
			roleResource.setAssigned(roleResourceIds.contains(resource.getId()));
			moduleRoleResource.getRoleResources().add(roleResource);
		}

		List<ModuleRoleResource> result = new ArrayList<>(moduleRoleResources.values());
		//按模块排序
		Collections.sort(result, new Comparator<ModuleRoleResource>() {
			@Override
			public int compare(ModuleRoleResource o1, ModuleRoleResource o2) {
				return o1.getModuleId().compareTo(o2.getModuleId());
			}
		});
		
		return result;
	}
	
	public List<TreeModel> findUserMenus(int accountId){
		//
		Map<Integer, ModuleEntity> modules = moduleService.getAllModules(true);
		
		Map<Integer, ResourceEntity> resourceMap = new HashMap<>();
		List<ResourceEntity> resources = resourceMapper.findNotLeafResources(ResourceType.menu.name());
		for (ResourceEntity resource : resources) {
			resourceMap.put(resource.getId(), resource);
		}
		
		resources = resourceMapper.findUserResources(accountId, ResourceType.menu.name());
		
		List<TreeModel> models = new ArrayList<>();
		ResourceEntity current;
		TreeModel model;
		for (ResourceEntity resource : resources) {
			if(resource.getModuleId() > 0 && !modules.containsKey(resource.getModuleId()))continue;
			models.add(new TreeModel(resource.getId(), resource.getName(),resource.getCode(), resource.getIcon(), resource.getParentId(), true));
			current = resource;
			while(current.hasChildren()){
				current = resourceMap.get(current.getParentId());
				model = new TreeModel(current.getId(), current.getName(),current.getCode(), current.getIcon(), current.getParentId(), false);
				if(!models.contains(model)){
					models.add(model);
				}
			}
		}
		return TreeModel.build(models).getChildren();
	}
	
	private List<TreeModel> buildResourceTree(List<ResourceEntity> resources) {
		
		Map<Integer, ModuleEntity> modules = moduleService.getAllModules(true);
		TreeModel treeModel;String moduleName;
		List<TreeModel> models = new ArrayList<>();
		for (ResourceEntity resource : resources) {
			if(!modules.containsKey(resource.getModuleId()))continue;
			treeModel = new TreeModel(resource.getId(), resource.getName(),resource.getCode(), resource.getIcon(), resource.getParentId(), resource.isLeaf());
			moduleName = modules.get(resource.getModuleId()).getName();
			treeModel.setExtraAttr(moduleName);
			models.add(treeModel);
		}
		
		return TreeModel.build(models).getChildren();
	}
	
	public ResourceEntity findById(int id){
		ResourceEntity entity = resourceMapper.selectByPrimaryKey(id);
		return entity;
	}
	
	public void addResource(int operUserId,ResourceParam param){
		if(param.getParentId() != null && param.getParentId() > 0){
			ResourceEntity parent = resourceMapper.selectByPrimaryKey(param.getParentId());
			param.setModuleId(parent.getModuleId());
		}
		AssertUtil.isNull(resourceMapper.findByModuleAndCode(param.getModuleId(), param.getCode()), "uri或编码重复");
		ResourceEntity entity = BeanCopyUtils.copy(param, ResourceEntity.class);
		entity.setCreatedAt(new Date());
		entity.setCreatedBy(operUserId);
		resourceMapper.insertSelective(entity);
	}
	
	
	public void updateResource(int operUserId,ResourceParam param){
		ResourceEntity entity = resourceMapper.selectByPrimaryKey(param.getId());
		AssertUtil.notNull(entity);
		ResourceEntity sameCodeEntity = resourceMapper.findByModuleAndCode(param.getModuleId(), param.getCode());
		if(sameCodeEntity != null && !sameCodeEntity.getId().equals(entity.getId())){
			throw new JeesuiteBaseException(ExceptionCode.REQUEST_DUPLICATION.code, "url或者编码重复");
		}
		entity.setCode(param.getCode());
		entity.setIcon(param.getIcon());
		entity.setName(param.getName());
		entity.setSort(param.getSort());
		entity.setUpdatedAt(new Date());
		entity.setUpdatedBy(operUserId);
		
		resourceMapper.insertSelective(entity);
	}
	
	@Transactional
	public void deleteResource(int operUserId,int id){
		resourceMapper.deleteResourceRalations(id);
		resourceMapper.deleteByPrimaryKey(id);
	}
	
	public void switchResource(int operUserId,Integer id,boolean enable){
		ResourceEntity entity = resourceMapper.selectByPrimaryKey(id);
		AssertUtil.notNull(entity);
    	if(entity.getEnabled() == enable)return;
    	entity.setEnabled(enable);
    	
    	entity.setUpdatedAt(new Date());
    	entity.setUpdatedBy(operUserId);
    	
    	resourceMapper.updateByPrimaryKey(entity);
	}
	
}
