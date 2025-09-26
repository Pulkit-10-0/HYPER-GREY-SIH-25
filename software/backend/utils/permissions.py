"""
Role-Based Access Control (RBAC) System
Defines permissions, roles, and access control logic
"""
from enum import Enum
from typing import List, Dict, Set


class Permission(Enum):
    """System permissions enumeration"""
    # User management
    USER_CREATE = "user.create"
    USER_READ = "user.read"
    USER_UPDATE = "user.update"
    USER_DELETE = "user.delete"
    
    # Device management
    DEVICE_CREATE = "device.create"
    DEVICE_READ = "device.read"
    DEVICE_UPDATE = "device.update"
    DEVICE_DELETE = "device.delete"
    DEVICE_CALIBRATE = "device.calibrate"
    
    # Data operations
    DATA_READ = "data.read"
    DATA_ANALYZE = "data.analyze"
    DATA_EXPORT = "data.export"
    
    # Report generation
    REPORT_GENERATE = "report.generate"
    REPORT_READ = "report.read"
    REPORT_DELETE = "report.delete"
    
    # Organization management
    ORG_READ = "organization.read"
    ORG_UPDATE = "organization.update"
    ORG_SETTINGS = "organization.settings"
    
    # System administration
    SYSTEM_ADMIN = "system.admin"
    SYSTEM_MONITOR = "system.monitor"
    SYSTEM_BACKUP = "system.backup"


class Role(Enum):
    """System roles enumeration"""
    ADMIN = "admin"
    MANAGER = "manager"
    OPERATOR = "operator"
    VIEWER = "viewer"


class RBACManager:
    """Role-Based Access Control Manager"""
    
    def __init__(self):
        self._role_permissions = self._initialize_role_permissions()
    
    def _initialize_role_permissions(self) -> Dict[Role, Set[Permission]]:
        """Initialize role-permission mappings"""
        return {
            Role.ADMIN: {
                # Full system access
                Permission.USER_CREATE, Permission.USER_READ, Permission.USER_UPDATE, Permission.USER_DELETE,
                Permission.DEVICE_CREATE, Permission.DEVICE_READ, Permission.DEVICE_UPDATE, 
                Permission.DEVICE_DELETE, Permission.DEVICE_CALIBRATE,
                Permission.DATA_READ, Permission.DATA_ANALYZE, Permission.DATA_EXPORT,
                Permission.REPORT_GENERATE, Permission.REPORT_READ, Permission.REPORT_DELETE,
                Permission.ORG_READ, Permission.ORG_UPDATE, Permission.ORG_SETTINGS,
                Permission.SYSTEM_ADMIN, Permission.SYSTEM_MONITOR, Permission.SYSTEM_BACKUP
            },
            
            Role.MANAGER: {
                # Management level access
                Permission.USER_READ, Permission.USER_UPDATE,
                Permission.DEVICE_CREATE, Permission.DEVICE_READ, Permission.DEVICE_UPDATE, Permission.DEVICE_CALIBRATE,
                Permission.DATA_READ, Permission.DATA_ANALYZE, Permission.DATA_EXPORT,
                Permission.REPORT_GENERATE, Permission.REPORT_READ,
                Permission.ORG_READ, Permission.SYSTEM_MONITOR
            },
            
            Role.OPERATOR: {
                # Operational access
                Permission.DEVICE_READ, Permission.DEVICE_UPDATE, Permission.DEVICE_CALIBRATE,
                Permission.DATA_READ, Permission.DATA_ANALYZE,
                Permission.REPORT_READ, Permission.ORG_READ
            },
            
            Role.VIEWER: {
                # Read-only access
                Permission.DEVICE_READ, Permission.DATA_READ, Permission.REPORT_READ, Permission.ORG_READ
            }
        }
    
    def get_role_permissions(self, role: str) -> Set[Permission]:
        """Get permissions for a role"""
        try:
            role_enum = Role(role.lower())
            return self._role_permissions.get(role_enum, set())
        except ValueError:
            return set()
    
    def has_permission(self, user_role: str, required_permission: str) -> bool:
        """Check if user role has required permission"""
        try:
            permission_enum = Permission(required_permission)
            role_permissions = self.get_role_permissions(user_role)
            return permission_enum in role_permissions
        except ValueError:
            return False
    
    def can_access_resource(self, user_role: str, resource_type: str, action: str) -> bool:
        """Check if user can perform action on resource type"""
        permission_string = f"{resource_type}.{action}"
        return self.has_permission(user_role, permission_string)
    
    def get_accessible_resources(self, user_role: str) -> Dict[str, List[str]]:
        """Get all resources and actions accessible to a role"""
        role_permissions = self.get_role_permissions(user_role)
        
        resources = {}
        for permission in role_permissions:
            resource, action = permission.value.split('.', 1)
            if resource not in resources:
                resources[resource] = []
            resources[resource].append(action)
        
        return resources
    
    def is_role_hierarchy_valid(self, current_role: str, target_role: str) -> bool:
        """Check if current role can manage target role (hierarchy validation)"""
        role_hierarchy = {
            Role.ADMIN: 4,
            Role.MANAGER: 3,
            Role.OPERATOR: 2,
            Role.VIEWER: 1
        }
        
        try:
            current_level = role_hierarchy.get(Role(current_role.lower()), 0)
            target_level = role_hierarchy.get(Role(target_role.lower()), 0)
            
            # Can only manage roles at same level or below
            return current_level >= target_level
        except ValueError:
            return False
    
    def get_manageable_roles(self, user_role: str) -> List[str]:
        """Get list of roles that user can manage"""
        role_hierarchy = {
            Role.ADMIN: [Role.ADMIN, Role.MANAGER, Role.OPERATOR, Role.VIEWER],
            Role.MANAGER: [Role.OPERATOR, Role.VIEWER],
            Role.OPERATOR: [],
            Role.VIEWER: []
        }
        
        try:
            role_enum = Role(user_role.lower())
            manageable = role_hierarchy.get(role_enum, [])
            return [role.value for role in manageable]
        except ValueError:
            return []


class ResourceAccessControl:
    """Resource-level access control"""
    
    def __init__(self, rbac_manager: RBACManager):
        self.rbac = rbac_manager
    
    def can_access_device(self, user_role: str, user_org_id: str, device_org_id: str, action: str) -> bool:
        """Check if user can access specific device"""
        # Check role permission
        if not self.rbac.can_access_resource(user_role, "device", action):
            return False
        
        # Admin can access all devices
        if user_role.lower() == "admin":
            return True
        
        # Non-admin users can only access devices in their organization
        return user_org_id == device_org_id
    
    def can_access_user_data(self, current_user_role: str, current_user_org: str, 
                           target_user_role: str, target_user_org: str, action: str) -> bool:
        """Check if user can access another user's data"""
        # Check role permission
        if not self.rbac.can_access_resource(current_user_role, "user", action):
            return False
        
        # Admin can access all users
        if current_user_role.lower() == "admin":
            return True
        
        # Same organization check
        if current_user_org != target_user_org:
            return False
        
        # Role hierarchy check for management actions
        if action in ["update", "delete"]:
            return self.rbac.is_role_hierarchy_valid(current_user_role, target_user_role)
        
        return True
    
    def can_access_organization_data(self, user_role: str, user_org_id: str, 
                                   target_org_id: str, action: str) -> bool:
        """Check if user can access organization data"""
        # Check role permission
        if not self.rbac.can_access_resource(user_role, "organization", action):
            return False
        
        # Admin can access all organizations
        if user_role.lower() == "admin":
            return True
        
        # Non-admin users can only access their own organization
        return user_org_id == target_org_id
    
    def filter_devices_by_access(self, devices: List[Dict], user_role: str, user_org_id: str) -> List[Dict]:
        """Filter device list based on user access"""
        if user_role.lower() == "admin":
            return devices
        
        # Filter devices by organization
        return [device for device in devices if device.get('organization_id') == user_org_id]
    
    def filter_users_by_access(self, users: List[Dict], current_user_role: str, 
                              current_user_org: str) -> List[Dict]:
        """Filter user list based on access permissions"""
        if current_user_role.lower() == "admin":
            return users
        
        # Filter users by organization
        filtered_users = [user for user in users if user.get('organization_id') == current_user_org]
        
        # Further filter based on role hierarchy for management roles
        if current_user_role.lower() in ["manager"]:
            manageable_roles = self.rbac.get_manageable_roles(current_user_role)
            filtered_users = [user for user in filtered_users 
                            if user.get('role', '').lower() in manageable_roles]
        
        return filtered_users


# Global RBAC instance
rbac_manager = RBACManager()
resource_access_control = ResourceAccessControl(rbac_manager)


def get_user_permissions(user_role: str) -> List[str]:
    """Get list of permissions for user role"""
    permissions = rbac_manager.get_role_permissions(user_role)
    return [perm.value for perm in permissions]


def check_permission(user_role: str, required_permission: str) -> bool:
    """Check if user has required permission"""
    return rbac_manager.has_permission(user_role, required_permission)


def check_resource_access(user_role: str, user_org_id: str, resource_org_id: str, 
                         resource_type: str, action: str) -> bool:
    """Check resource-level access"""
    if resource_type == "device":
        return resource_access_control.can_access_device(
            user_role, user_org_id, resource_org_id, action
        )
    elif resource_type == "organization":
        return resource_access_control.can_access_organization_data(
            user_role, user_org_id, resource_org_id, action
        )
    else:
        return rbac_manager.can_access_resource(user_role, resource_type, action)