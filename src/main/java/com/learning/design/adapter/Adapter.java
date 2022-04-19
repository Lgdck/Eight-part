package com.learning.design.adapter;

/**
 * <pre>
 * @Description:
 * TODO
 * </pre>
 *
 * @version v1.0
 * @ClassName: Adapter
 * @Author: sanwu
 * @Date: 2020/5/10 23:45
 */
public class Adapter extends Adaptee implements Target {
    private Adaptee target;

    public Adapter(Adaptee target) {
        this.target = target;
    }
    @Override
    public void execute() {
        target.invokeExecute();
    }
}
