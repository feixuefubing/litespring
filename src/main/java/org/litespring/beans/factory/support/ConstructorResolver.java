package org.litespring.beans.factory.support;

import java.lang.reflect.Constructor;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.litespring.beans.BeanDefinition;
import org.litespring.beans.ConstructorArgument;
import org.litespring.beans.SimpleTypeConverter;
import org.litespring.beans.factory.BeanCreationException;
import org.litespring.beans.factory.config.ConfigurableBeanFactory;


//找到合适的构造函数并且用该构造函数创建一个对象
public class ConstructorResolver {

	protected final Log logger = LogFactory.getLog(getClass());
	
	
	private final ConfigurableBeanFactory beanFactory;


	
	public ConstructorResolver(ConfigurableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	
	public Object autowireConstructor(final BeanDefinition bd) {
		//想要的构造函数
		Constructor<?> constructorToUse = null;	
		
		//记录用于创建对象的参数
		Object[] argsToUse = null;
	
		Class<?> beanClass = null;
		
		//可以缓存到beanDefinition当中
		//load装载比较费时
		try {
			beanClass = this.beanFactory.getBeanClassLoader().loadClass(bd.getBeanClassName());
			
		} catch (ClassNotFoundException e) {
			throw new BeanCreationException( bd.getID(), "Instantiation of bean failed, can't resolve class", e);
		}	
		
		//通过反射拿到
		Constructor<?>[] candidates = beanClass.getConstructors();	
		
		
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory);
		
		ConstructorArgument cargs = bd.getConstructorArgument();
		//字符串转整型
		SimpleTypeConverter typeConverter = new SimpleTypeConverter();
		
		for(int i=0; i<candidates.length;i++){
			
			Class<?> [] parameterTypes = candidates[i].getParameterTypes();
			//数量不等则该构造函数不能用 进入下一个循环
			if(parameterTypes.length != cargs.getArgumentCount()){
				continue;
			}			
			argsToUse = new Object[parameterTypes.length];
			
			//看值是否和map匹配
			boolean result = this.valuesMatchTypes(parameterTypes, 
					cargs.getArgumentValues(), 
					argsToUse, 
					valueResolver, 
					typeConverter);
			
			//目前找到一个可用的构造函数就直接返回了
			if(result){
				constructorToUse = candidates[i];
				break;
			}
			
		}
		
		
		//找不到一个合适的构造函数
		if(constructorToUse == null){
			throw new BeanCreationException( bd.getID(), "can't find a apporiate constructor");
		}
		
		
		try {
			//创建对象
			return constructorToUse.newInstance(argsToUse);
		} catch (Exception e) {
			throw new BeanCreationException( bd.getID(), "can't find a create instance using "+constructorToUse);
		}		
		
		
	}
	
	//参数：类的类型，xml中对constructor-arg的值描述，空的数组，要用的，转换器
	private boolean valuesMatchTypes(Class<?> [] parameterTypes,
			List<ConstructorArgument.ValueHolder> valueHolders,
			Object[] argsToUse,
			BeanDefinitionValueResolver valueResolver,
			SimpleTypeConverter typeConverter ){
		
		
		for(int i=0;i<parameterTypes.length;i++){
			ConstructorArgument.ValueHolder valueHolder 
				= valueHolders.get(i);
			//获取参数的值，可能是TypedStringValue, 也可能是RuntimeBeanReference
			Object originalValue = valueHolder.getValue();
			
			try{
				//获得真正的值
				Object resolvedValue = valueResolver.resolveValueIfNecessary(originalValue);
				//如果参数类型是 int, 但是值是字符串,例如"3",还需要转型
				//如果转型失败，则抛出异常。说明这个构造函数不可用
				Object convertedValue = typeConverter.convertIfNecessary(resolvedValue, parameterTypes[i]);
				//转型成功，记录下来
				argsToUse[i] = convertedValue;
			}catch(Exception e){
				logger.error(e);
				return false;
			}				
		}
		return true;
	}
	

}
