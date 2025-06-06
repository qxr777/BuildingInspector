package com.ruoyi.framework.shiro.realm;

import java.util.HashSet;
import java.util.Set;
import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.UserStatus;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.RoleBlockedException;
import com.ruoyi.common.exception.user.UserBlockedException;
import com.ruoyi.common.exception.user.UserDeleteException;
import com.ruoyi.common.exception.user.UserNotExistsException;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.common.exception.user.UserPasswordRetryLimitExceedException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.framework.jwt.auth.JwtToken;
import com.ruoyi.framework.jwt.utils.JwtUtils;
import com.ruoyi.framework.shiro.service.SysLoginService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 自定义Realm 处理登录 权限
 * 
 * @author ruoyi
 */
public class UserRealm extends AuthorizingRealm
{
    private static final Logger log = LoggerFactory.getLogger(UserRealm.class);

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysUserService userService;

    /**
     * 授权
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection arg0)
    {
        SysUser user = ShiroUtils.getSysUser();
        // 角色列表
        Set<String> roles = new HashSet<String>();
        // 功能列表
        Set<String> menus = new HashSet<String>();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        // 管理员拥有所有权限
        if (user.isAdmin())
        {
            info.addRole("admin");
            info.addStringPermission("*:*:*");
        }
        else
        {
            roles = roleService.selectRoleKeys(user.getUserId());
            menus = menuService.selectPermsByUserId(user.getUserId());
            // 角色加入AuthorizationInfo认证对象
            info.setRoles(roles);
            // 权限加入AuthorizationInfo认证对象
            info.setStringPermissions(menus);
        }
        return info;
    }

    /**
     * 登录认证
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
            throws AuthenticationException
    {
        if (authenticationToken instanceof JwtToken)
        {
            JwtToken jwtToken = (JwtToken) authenticationToken;
            String token = jwtToken.getToken();
            String username = JwtUtils.getUserName(token);
            if (username == null)
            {
                throw new AccountException("token 验证失败");
            }
            SysUser user = userService.selectUserByLoginName(username);
            if (user == null)
            {
                throw new AuthenticationException("用户数据不存在");
            }

            try
            {
                JwtUtils.verify(username, user.getPassword(), jwtToken.getToken());

                if (UserStatus.DELETED.getCode().equals(user.getDelFlag()))
                {
                    throw new UserDeleteException();
                }

                if (UserStatus.DISABLE.getCode().equals(user.getStatus()))
                {
                    throw new UserBlockedException();
                }

                loginService.setRolePermission(user);
            }
            catch (Exception e)
            {
                log.info("对用户[" + username + "]进行jwt登录验证..验证未通过{}", e.getMessage());
                throw new AuthenticationException(e.getMessage(), e);
            }

            return new SimpleAuthenticationInfo(user, null, getName());
        }
        else
        {
            UsernamePasswordToken upToken = (UsernamePasswordToken) authenticationToken;
            String username = upToken.getUsername();
            String password = "";
            if (upToken.getPassword() != null)
            {
                password = new String(upToken.getPassword());
            }

            SysUser user = null;
            try
            {
                user = loginService.login(username, password);
            }
            catch (CaptchaException e)
            {
                throw new AuthenticationException(e.getMessage(), e);
            }
            catch (UserNotExistsException e)
            {
                throw new UnknownAccountException(e.getMessage(), e);
            }
            catch (UserPasswordNotMatchException e)
            {
                throw new IncorrectCredentialsException(e.getMessage(), e);
            }
            catch (UserPasswordRetryLimitExceedException e)
            {
                throw new ExcessiveAttemptsException(e.getMessage(), e);
            }
            catch (UserBlockedException e)
            {
                throw new LockedAccountException(e.getMessage(), e);
            }
            catch (RoleBlockedException e)
            {
                throw new LockedAccountException(e.getMessage(), e);
            }
            catch (Exception e)
            {
                log.info("对用户[" + username + "]进行登录验证..验证未通过{}", e.getMessage());
                throw new AuthenticationException(e.getMessage(), e);
            }
            SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(user, null, getName());
            return info;
        }
    }

    /**
     * 清理指定用户授权信息缓存
     */
    public void clearCachedAuthorizationInfo(Object principal)
    {
        SimplePrincipalCollection principals = new SimplePrincipalCollection(principal, getName());
        this.clearCachedAuthorizationInfo(principals);
    }

    /**
     * 清理所有用户授权信息缓存
     */
    public void clearAllCachedAuthorizationInfo()
    {
        Cache<Object, AuthorizationInfo> cache = getAuthorizationCache();
        if (cache != null)
        {
            for (Object key : cache.keys())
            {
                cache.remove(key);
            }
        }
    }
}
